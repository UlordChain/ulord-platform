/**
 * Copyright(c) 2018
 * Ulord core developers
 */
package one.ulord.upaas.ucwallet.client;

import one.ulord.upaas.ucwallet.client.contract.generates.CenterPublish;
import one.ulord.upaas.ucwallet.client.contract.generates.MulTransfer;
import one.ulord.upaas.ucwallet.client.contract.generates.UshareToken;
import one.ulord.upaas.ucwallet.client.utils.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Content contract
 * @author haibo
 * @since 7/5/18
 */
public class ContentContract {
    final Logger logger = LoggerFactory.getLogger(ContentContract.class);

    /**
     * Block max gas limit
     */
    public static BigInteger BLOCK_GAS_LIMIT = BigInteger.valueOf(6700000);
    /**
     * Min gas price
     */
    public static BigInteger GAS_PRICE = BigInteger.valueOf(200000000); //0.2GWei

    public static long TX_CONFIRM_TIME_MS = 80000;
    public static long TX_QUERY_LOOP_MS = 5000;

    private String tokenAddress;
    private String uxCandyAddress;
    private String publishAddress;
    private String keystoreFile;
    private String mainAddress;

    private UshareToken ushToken;
    private MulTransfer uxCandy;
    private CenterPublish centerPublish;
    private Transfer transfer;

    private TransactionActionHandler handler;

    Credentials credentials;

    FastRawTransactionManager transactionManager;

    private Web3j web3j;


    /**
     * Build a new content smart contract instance. It will take more that 10 seconds to build connection
     * and contract instance.
     * @param ulordProvider Ulord side provider, such as http://xxxx:yyy, which is a RPC endpoint
     * @param tokenAddress token address which has deploy to ulord side chain
     * @param candyAddress a candy contract for Token which is for multi-pay function
     * @param publishAddress a publish smart contract which has deply to ulord side chain
     * @param keystoreFile user account keystore file, which include user account private key
     * @param keystorePassword user account keystore password
     * @param handler a {@line TransactionActionHandler} instance
     * @throws IOException
     * @throws CipherException
     */
    public ContentContract(String ulordProvider, String tokenAddress, String candyAddress, String publishAddress,
                           String keystoreFile, String keystorePassword, TransactionActionHandler handler)
            throws IOException, CipherException {
        this.tokenAddress = tokenAddress;
        this.uxCandyAddress = candyAddress;
        this.publishAddress = publishAddress;

        this.keystoreFile = keystoreFile;
        this.handler = handler;


        this.web3j = Web3j.build(new HttpService(ulordProvider));
        Web3ClientVersion web3ClientVersion = null;
        web3ClientVersion = web3j.web3ClientVersion().send();
        String clientVersion = web3ClientVersion.getWeb3ClientVersion();
        if (clientVersion == null){
            throw new IOException("Ulord provider cannot connect.");
        }

        URL fileUrl = Loader.getResource(keystoreFile);
        File file = null;
        if (fileUrl == null){
            file = new File(this.keystoreFile);
            if (!file.exists()){
                throw new IOException("Cannot found keystore file.");
            }
        }else{
            try {
                file = new File(fileUrl.toURI());
            } catch (URISyntaxException e) {
                throw new IOException(e.getMessage());
            }
        }
        this.credentials = WalletUtils.loadCredentials(keystorePassword, file);
        this.mainAddress = credentials.getAddress();

        // we need using fast transaction manager
        transactionManager = new FastRawTransactionManager(web3j, credentials);

        transfer = new Transfer(web3j, transactionManager);

        // load contract object
        this.ushToken = UshareToken.load(tokenAddress, web3j, transactionManager,
                DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT);
        this.uxCandy = MulTransfer.load(uxCandyAddress, web3j, transactionManager,
                DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT);
        this.centerPublish = CenterPublish.load(publishAddress, web3j, transactionManager,
                DefaultGasProvider.GAS_PRICE, ContentContract.BLOCK_GAS_LIMIT); // Using block max limit

        logger.info("Load Content Contract Success.");
    }


    /**
     * Get ulord side chain gas balance
     * @return gas balance (Unit SUT)
     * @throws IOException
     */
    public BigInteger getGasBalance() throws IOException {
        BigInteger balance = web3j.ethGetBalance(this.mainAddress, DefaultBlockParameterName.LATEST).send().getBalance();
        return balance;
    }

    /**
     * Get ulord side chain gas balance for a specified address
     * @return Gas balance, Unit SUT
     * @throws IOException
     */
    public BigInteger getGasBalance(String address) throws IOException {
        BigInteger balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
        return balance;
    }

    /**
     * Get ulord side chain token balance
     * @return token balance (Unit UX)
     * @throws Exception
     */
    public BigInteger getTokenBalance() throws Exception {
        return getTokenBalance(this.mainAddress);
    }

    public BigInteger getTokenBalance(String address) throws Exception {
        BigInteger value = ushToken.balanceOf(address).send();
        return value;
    }

    /**
     * Transfer amount of gas from main address to specified address
     * @param reqId request id
     * @param toAddress target address
     * @param value gas value
     * @throws IOException IOException while send a RPC call
     */
    public void transferGas(final String reqId, String toAddress, BigInteger value) {
        // transfer using fast transaction manager
        transfer.sendFunds(toAddress, new BigDecimal(value), Convert.Unit.WEI, GAS_PRICE, DefaultGasProvider.GAS_LIMIT)
                .sendAsync().whenCompleteAsync((receipt, e)->{
                    if (e == null && receipt != null){
                        processTransactionReceipt(reqId, receipt);
                    }else{
                        processTransactionException(reqId, e);
                    }
        });
    }

    private void processTransactionException(String reqId, Throwable e) {
        // we need reset nonce
        resetNonce();
        logger.warn("Transaction exception:" +  reqId + ", " + e.getMessage());
        this.handler.fail(reqId, e.getMessage());
    }

    private void resetNonce() {
        transactionManager.setNonce(BigInteger.valueOf(-1));
        logger.info("RESET NONCE VALUE:" + transactionManager.getCurrentNonce());
    }

    /**
     * Transfer amount of token to a specified address
     * @param reqId request id
     * @param toAddress a target address
     * @param quantity quantity
     */
    public void transferToken(final String reqId, String toAddress, BigInteger quantity){
        ushToken.transfer(toAddress, quantity).sendAsync().whenCompleteAsync((receipt, e) -> {
            if (e == null){
                processTransactionReceipt(reqId, receipt);
            }else{
                processTransactionException(reqId, e);
            }
        });
    }

    /**
     * Approve publish contract to use a quality tokens
     * @param reqId request id
     * @param quantity a quality
     */
    public void approveContractQuality(final String reqId, BigInteger quantity){
        CompletableFuture<TransactionReceipt> future = ushToken.approve(publishAddress, quantity).sendAsync();
        future.whenCompleteAsync((receipt, e)->{
           if (e == null){
               processTransactionReceipt(reqId, receipt);
           }else{
               processTransactionException(reqId, e);
           }
        });

    }

    /**
     * Publish a resource to ulord smart contract.
     * While
     * @param reqId request id
     * @param udfsHash UDFS hash, must get from UDFS {@link UDFSClient}
     * @param authorAddress author address
     * @param price price
     * @param deposit deposit
     */
    public void publishResource(final String reqId, String udfsHash,
                                String authorAddress, BigInteger price, BigInteger deposit){
        // Using RxJava to process sync
        centerPublish.createClaim(udfsHash, authorAddress,
                price, deposit, new BigInteger("1")).sendAsync().whenCompleteAsync((receipt, e)-> {
                    if (e == null){
                        processTransactionReceipt(reqId, receipt);
                    }else{
                        processTransactionException(reqId, e);
                    }
                });
    }

    /**
     * Transfer to multiple address using different quality from current address
     * @param reqId request id
     * @param address a set of target address
     * @param quality a set of quality need to transfer
     */
    public void transferTokens(final String reqId, List<String> address, List<BigInteger> quality){
        if (address == null || quality == null || address.size() == 0 || address.size() != quality.size()){
            throw new RuntimeException("Invalid parameters, master equal.");
        }
        if (address.size() > 200){
            logger.warn("Submit address amount more than 200, the transaction maybe out of gas.");
        }
        uxCandy.mulPayDiff(address, quality).sendAsync().whenCompleteAsync((receipt, e)-> {
            if (e == null){
                processTransactionReceipt(reqId, receipt);
            }else{
                processTransactionException(reqId, e);
            }
        });
    }

    /**
     * Transfer to multiple address using same quality from current address
     * @param reqId request id
     * @param address a set of target address
     * @param quality a set of quality need to transfer
     */
    public void transferTokens(final String reqId, BigInteger quality, List<String> address){
        if (address == null || quality == null || address.size() == 0){
            throw new RuntimeException("Invalid parameters, master equal.");
        }
        if (address.size() > 200){
            logger.warn("Submit address amount more than 200, the transaction maybe out of gas.");
        }
        uxCandy.mulPaySame(quality, address).sendAsync().whenCompleteAsync((receipt, e)-> {
            if (e == null){
                processTransactionReceipt(reqId, receipt);
            }else{
                processTransactionException(reqId, e);
            }
        });
    }

    /**
     * Return a raw transaction hash. [Sync]
     * @param txhash transaction hash
     * @return hash
     */
    public TransactionReceipt queryTransactionReceipt(String txhash) throws IOException {
        return web3j.ethGetTransactionReceipt(txhash).send().getResult();
    }

    private void processTransactionReceipt(String reqId, TransactionReceipt transactionReceipt) {
        if (transactionReceipt.isStatusOK()) {
            if (this.handler != null){
                this.handler.success(reqId, transactionReceipt.getTransactionHash());
            }
        }else{
            if (this.handler != null){
                this.handler.fail(reqId,
                        "Unknown exception, the receipt has received:"
                                + transactionReceipt.getTransactionHash());
            }
        }
    }

}
