package com.deeptrust.blockchain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.ContractGasProvider;

@Configuration
public class Web3jConfig {

    @Value("${deeptrust.blockchain.rpc-url:http://127.0.0.1:8545}")
    private String rpcUrl;

    // Private key injected via env var only — never committed. For Ganache
    // local testnet this is one of the pre-funded dev accounts; in any
    // real deployment this MUST come from a secrets manager / HSM, never
    // a plain application.yml value.
    @Value("${deeptrust.blockchain.wallet-private-key}")
    private String walletPrivateKey;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    public Credentials credentials() {
        return Credentials.create(walletPrivateKey);
    }

    @Bean
    public ContractGasProvider gasProvider() {
        // Ganache defaults are generous; tune for real networks / L2s.
        return new DefaultGasProvider();
    }
}
