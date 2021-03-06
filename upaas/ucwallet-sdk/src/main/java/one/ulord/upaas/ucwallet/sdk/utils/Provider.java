/**
 * Copyright(c) 2018
 * Ulord core developers
 */
package one.ulord.upaas.ucwallet.sdk.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ulord.side.provider config info
 *
 * @author chenxin
 * @since 2018-08-10
 */
@Component
public class Provider {

    @Value("${udfs.gateway}")
    private String udfsGateway;

    public String getUdfsGateway() {
        return udfsGateway;
    }
}
