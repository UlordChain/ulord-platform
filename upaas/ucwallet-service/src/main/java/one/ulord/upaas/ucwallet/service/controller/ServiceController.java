/**
 * Copyright(c) 2018
 * Ulord core developers
 */
package one.ulord.upaas.ucwallet.service.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import one.ulord.upaas.ucwallet.service.base.common.JsonResult;
import one.ulord.upaas.ucwallet.service.base.common.ResultUtil;
import one.ulord.upaas.ucwallet.service.base.contract.Provider;
import one.ulord.upaas.ucwallet.service.service.SUTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;

import static one.ulord.upaas.ucwallet.service.base.common.Constants.INVALID_NONCE_VALUE;
import static one.ulord.upaas.ucwallet.service.base.common.Constants.NO_ENOUGH_SUT;

/**
 * ucwallet-service API Service
 * 
 * @author chenxin, yinhaibo
 * @since 2018-08-10
 */
@Api(value = "Service Center")
@RestController
@RequestMapping(value = "api")
public class ServiceController{
	private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

	@Autowired
    private SUTService sutService;

    @Autowired
    private Provider provider;

	/**
	 * Get balance by address
	 * @param address
	 * @return
	 */
	@ApiOperation(value = "Get balance by address", notes = "Get balance by address")
	@RequestMapping(value = "getBalance/{address}", method = RequestMethod.GET)
	public ResponseEntity<String> getSutBalance(@PathVariable(value="address") String address) {
		logger.debug("getSutBalance address:"+address);

		try {
            String gasBalance = sutService.getBalance(address).toString();
			logger.debug("address{}, balance:{}", address, gasBalance);
            return ResultUtil.GoResponseSuccess(gasBalance);
		} catch (Exception e) {
			logger.error("get balance error:", e);
			return ResultUtil.GoResponseFailure(e.getMessage());
		}
	}

	/**
	 * Get token balance by address
	 * @param address
	 * @return
	 */
	@ApiOperation(value = "Get token balance by address", notes = "Get token balance by address")
	@RequestMapping(value = "getTokenBalance/{address}", method = RequestMethod.GET)
	public ResponseEntity<String> getTokenBalance(@PathVariable(value="address") String address, String token) {
		logger.info("address:{}, token:{}", address, token);

		if (token == null){
		    // using default contract address
            token = provider.getContractAddress();
        }
		try {
            String tokenBalance = sutService.getTokenBalance(token, address).toString();
			logger.info("Token balance:" + tokenBalance);
            return ResultUtil.GoResponseSuccess(tokenBalance);
		} catch (Exception e) {
            logger.error("get token balance error:", e);
			return ResultUtil.GoResponseFailure(e.getMessage());
		}
	}


	/**
	 * Get transaction count by address
	 * @param address
	 * @return
	 */
	@ApiOperation(value = "Get transaction count by address", notes = "Get transaction count by address")
	@RequestMapping(value = "getTransactionCount/{address}", method = RequestMethod.GET)
	public ResponseEntity<String> getTransactionCount(@PathVariable(value="address") String address) {
		try {
            String nonce = sutService.getTransactionCount(address).toString();
			logger.debug("address:{} nonce:{}", address, nonce);
            return ResultUtil.GoResponseSuccess(nonce);
		} catch (Exception e) {
			return ResultUtil.GoResponseFailure(e.getMessage());
		}
	}

	/**
	 * Send raw transaction
	 * @param hexValue
	 * @return
	 */
	@ApiOperation(value = "Send raw transaction", notes = "Send raw transaction")
	@RequestMapping(value = "sendRawTransaction", method = RequestMethod.POST)
	public ResponseEntity<String> sendRawTransaction(@RequestParam String hexValue) {
		logger.debug("hexValue:{}", hexValue);

		String hash;
		try {
            RawTransaction tx;
            tx = TransactionDecoder.decode(hexValue);
            if (tx instanceof SignedRawTransaction) {
                String from = ((SignedRawTransaction) tx).getFrom().toLowerCase();
                BigInteger nonce = tx.getNonce();
                BigDecimal gasFee = sutService.fromWei(tx.getGasLimit().multiply(tx.getGasPrice()));
                BigDecimal value = sutService.fromWei(tx.getValue());

                BigInteger txCount = sutService.getTransactionCount(from);
                BigDecimal balance = sutService.getBalance(from);


                // check balance
                if (balance.subtract(gasFee).subtract(value).signum() < 0) {
                    // No more sUT
                    return ResultUtil.GoResponseFailure(NO_ENOUGH_SUT, "No enough sUT.");
                }

                // check nonce value
                if (nonce.compareTo(txCount) < 0) {
                    // Invalid nonce value
                    return ResultUtil.GoResponseFailure(INVALID_NONCE_VALUE, "Invalid nonce value");
                }
            }
			hash = sutService.sendRawTransaction(hexValue);
            return ResultUtil.GoResponseSuccess(hash);
		} catch (Exception e) {
		    logger.error("send raw transaction err:{}", hexValue, e);
			return ResultUtil.GoResponseFailure(e.getMessage());
		}
	}


	/**
	 * query transaction
	 * @param txhash transaction hash
	 * @return
	 */
	@ApiOperation(value = "query transaction by transaction hash", notes = "query transaction")
	@RequestMapping(value = "queryTransaction", method = RequestMethod.GET)
	public ResponseEntity<String> queryTransaction(@RequestParam String txhash) {
		JsonResult resultJson = new JsonResult();
		logger.info("getTransaction:{}", txhash);

		try {
            Transaction tx = sutService.getTransaction(txhash);
            return ResultUtil.GoResponseSuccess(tx);
		} catch (Exception e) {
            logger.error("query transaction for {}:", txhash, e);
            return ResultUtil.GoResponseFailure(resultJson);
		}
	}

	/**
	 * query transaction
	 * @param txhash transaction hash
	 * @return
	 */
	@ApiOperation(value = "query transaction receipt by transaction hash", notes = "query transaction receipt")
	@RequestMapping(value = "queryTransactionReceipt", method = RequestMethod.GET)
	public ResponseEntity<String> queryTransactionReceipt(@RequestParam String txhash) {
		logger.info("getTransactionReceipt" + txhash);

		try {
            TransactionReceipt receipt = sutService.getTransactionReceipt(txhash);
            return ResultUtil.GoResponseSuccess(receipt);
		} catch (Exception e) {
			logger.error("query transaction for transaction:{}", txhash, e);
			return ResultUtil.GoResponseFailure(e.getMessage());
		}
	}
}
