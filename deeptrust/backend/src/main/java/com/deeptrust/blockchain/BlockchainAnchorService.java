package com.deeptrust.blockchain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Bridges certificate issuance/revocation to DeepTrustRegistry.sol.
 * Only certificateId (hashed), sha256 hash, result code, and timestamp ever
 * cross this boundary — never PII, filenames, or file bytes.
 *
 * Uses manual ABI encoding via Web3j core types rather than a generated
 * contract wrapper, so the backend has no build-time dependency on the
 * Solidity compilation step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainAnchorService {

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider gasProvider;

    @Value("${deeptrust.blockchain.contract-address}")
    private String contractAddress;

    /** Result codes must match the Solidity enum order exactly: AUTHENTIC=0, MANIPULATED=1, INCONCLUSIVE=2. */
    public enum ResultCode {
        AUTHENTIC(0), MANIPULATED(1), INCONCLUSIVE(2);
        final int code;
        ResultCode(int code) { this.code = code; }
    }

    public record AnchorResult(String transactionHash, long blockNumber) {}

    public AnchorResult issueCertificate(String certificateCode, String sha256HashHex, ResultCode result) {
        Bytes32 certId = toBytes32(certificateCode);
        Bytes32 sha256Bytes = hexToBytes32(sha256HashHex);

        Function function = new Function(
                "issueCertificate",
                List.of(certId, sha256Bytes, new Uint8(BigInteger.valueOf(result.code))),
                Collections.emptyList()
        );

        TransactionReceipt receipt = sendTransaction(function);
        log.info("Certificate {} anchored on-chain, tx={}", certificateCode, receipt.getTransactionHash());
        return new AnchorResult(receipt.getTransactionHash(), receipt.getBlockNumber().longValue());
    }

    public AnchorResult revokeCertificate(String certificateCode, String reason) {
        Bytes32 certId = toBytes32(certificateCode);

        Function function = new Function(
                "revokeCertificate",
                List.of(certId, new Utf8String(reason)),
                Collections.emptyList()
        );

        TransactionReceipt receipt = sendTransaction(function);
        log.info("Certificate {} revoked on-chain, tx={}", certificateCode, receipt.getTransactionHash());
        return new AnchorResult(receipt.getTransactionHash(), receipt.getBlockNumber().longValue());
    }

    private TransactionReceipt sendTransaction(Function function) {
        try {
            String encodedFunction = FunctionEncoder.encode(function);
            TransactionManager txManager = new RawTransactionManager(
                    web3j, credentials, /* chainId auto-fetched */ web3j.ethChainId().send().getChainId().longValue());

            org.web3j.protocol.core.methods.response.EthSendTransaction tx = txManager.sendTransaction(
                    gasProvider.getGasPrice(),
                    gasProvider.getGasLimit(),
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (tx.hasError()) {
                throw new IllegalStateException("Blockchain transaction failed: " + tx.getError().getMessage());
            }

            String txHash = tx.getTransactionHash();
            return web3j.ethGetTransactionReceipt(txHash).send()
                    .getTransactionReceipt()
                    .orElseThrow(() -> new IllegalStateException("No receipt returned for tx " + txHash));

        } catch (Exception e) {
            log.error("Blockchain anchoring failed", e);
            throw new IllegalStateException("Failed to anchor data on blockchain: " + e.getMessage(), e);
        }
    }

    /**
     * Read-only cross-check: confirms the on-chain record for a certificate
     * is active and independently agrees with what MySQL reports. Used by
     * the public verification endpoint so a compromised MySQL row alone
     * cannot forge a "verified" result.
     */
    public boolean isActiveOnChain(String certificateCode) {
        try {
            Bytes32 certId = toBytes32(certificateCode);
            Function function = new Function(
                    "isActive",
                    List.of(certId),
                    List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
            );
            String encodedFunction = FunctionEncoder.encode(function);

            org.web3j.protocol.core.methods.request.Transaction callTx =
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(), contractAddress, encodedFunction);

            String result = web3j.ethCall(callTx, DefaultBlockParameterName.LATEST).send().getValue();
            List<Type> decoded = org.web3j.abi.FunctionReturnDecoder.decode(
                    result, function.getOutputParameters());
            return !decoded.isEmpty() && (Boolean) decoded.get(0).getValue();
        } catch (Exception e) {
            log.warn("On-chain isActive check failed for {}: {}", certificateCode, e.getMessage());
            return false;
        }
    }

    /** Deterministically maps a human-readable certificate code to a bytes32 on-chain identifier. */
    private Bytes32 toBytes32(String certificateCode) {
        byte[] hash = Hash.sha3(certificateCode.getBytes(StandardCharsets.UTF_8));
        return new Bytes32(hash);
    }

    private Bytes32 hexToBytes32(String hexHash) {
        byte[] bytes = Numeric.hexStringToByteArray(hexHash.length() == 64 ? hexHash : hexHash.substring(0, 64));
        return new Bytes32(bytes);
    }
}
