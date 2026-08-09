// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title DeepTrustRegistry
 * @notice Immutable anchor for DeepTrust certificates. Stores ONLY:
 *         certificate ID, SHA-256 media hash, analysis result code, timestamp.
 *         NEVER stores PII, filenames, or media bytes — those live exclusively
 *         in the MySQL operational database.
 *
 * Revocation model: this contract never deletes or mutates a certificate's
 * original record. Revoking appends a StatusChanged event and updates a
 * status flag — the original IssuedCertificate event remains forever queryable,
 * preserving a full, tamper-evident history (mirrors the CertificateStatusEvent
 * append-only log on the MySQL side).
 */
contract DeepTrustRegistry {

    enum ResultCode { AUTHENTIC, MANIPULATED, INCONCLUSIVE }
    enum Status { ACTIVE, REVOKED }

    struct CertificateRecord {
        bytes32 sha256Hash;
        ResultCode result;
        Status status;
        uint256 issuedAt;
        address issuer;
    }

    // certificateId (e.g. keccak256("DT-2026-000001")) => record
    mapping(bytes32 => CertificateRecord) private certificates;
    mapping(bytes32 => bool) private certificateExists;

    address public owner;
    mapping(address => bool) public authorizedIssuers;

    event CertificateIssued(
        bytes32 indexed certificateId,
        bytes32 sha256Hash,
        ResultCode result,
        uint256 timestamp,
        address indexed issuer
    );

    event CertificateStatusChanged(
        bytes32 indexed certificateId,
        Status newStatus,
        string reason,
        uint256 timestamp,
        address indexed actor
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "DeepTrustRegistry: caller is not owner");
        _;
    }

    modifier onlyAuthorizedIssuer() {
        require(authorizedIssuers[msg.sender], "DeepTrustRegistry: not an authorized issuer");
        _;
    }

    constructor() {
        owner = msg.sender;
        authorizedIssuers[msg.sender] = true;
    }

    function setIssuerAuthorization(address issuer, bool authorized) external onlyOwner {
        authorizedIssuers[issuer] = authorized;
    }

    /**
     * @notice Anchors a new certificate. Reverts if the certificateId is
     *         already registered — anchoring is create-only, never overwrite.
     */
    function issueCertificate(
        bytes32 certificateId,
        bytes32 sha256Hash,
        ResultCode result
    ) external onlyAuthorizedIssuer {
        require(!certificateExists[certificateId], "DeepTrustRegistry: certificate already exists");

        certificates[certificateId] = CertificateRecord({
            sha256Hash: sha256Hash,
            result: result,
            status: Status.ACTIVE,
            issuedAt: block.timestamp,
            issuer: msg.sender
        });
        certificateExists[certificateId] = true;

        emit CertificateIssued(certificateId, sha256Hash, result, block.timestamp, msg.sender);
    }

    /**
     * @notice Marks a certificate REVOKED. Does NOT delete or overwrite the
     *         original record — only the status field changes, and a
     *         CertificateStatusChanged event permanently records the action.
     */
    function revokeCertificate(bytes32 certificateId, string calldata reason) external onlyAuthorizedIssuer {
        require(certificateExists[certificateId], "DeepTrustRegistry: certificate does not exist");
        require(certificates[certificateId].status == Status.ACTIVE, "DeepTrustRegistry: already revoked");

        certificates[certificateId].status = Status.REVOKED;

        emit CertificateStatusChanged(certificateId, Status.REVOKED, reason, block.timestamp, msg.sender);
    }

    function getCertificate(bytes32 certificateId) external view returns (
        bytes32 sha256Hash,
        ResultCode result,
        Status status,
        uint256 issuedAt,
        address issuer
    ) {
        require(certificateExists[certificateId], "DeepTrustRegistry: certificate does not exist");
        CertificateRecord memory rec = certificates[certificateId];
        return (rec.sha256Hash, rec.result, rec.status, rec.issuedAt, rec.issuer);
    }

    function isActive(bytes32 certificateId) external view returns (bool) {
        return certificateExists[certificateId] && certificates[certificateId].status == Status.ACTIVE;
    }
}
