const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("DeepTrustRegistry", function () {
  let registry, owner, other;

  beforeEach(async function () {
    [owner, other] = await ethers.getSigners();
    const Factory = await ethers.getContractFactory("DeepTrustRegistry");
    registry = await Factory.deploy();
    await registry.waitForDeployment();
  });

  it("issues a certificate and marks it ACTIVE", async function () {
    const certId = ethers.keccak256(ethers.toUtf8Bytes("DT-2026-000001"));
    const sha256Hash = ethers.keccak256(ethers.toUtf8Bytes("dummy-file-content"));

    await registry.issueCertificate(certId, sha256Hash, 0 /* AUTHENTIC */);
    expect(await registry.isActive(certId)).to.equal(true);
  });

  it("prevents duplicate certificate IDs", async function () {
    const certId = ethers.keccak256(ethers.toUtf8Bytes("DT-2026-000002"));
    const sha256Hash = ethers.keccak256(ethers.toUtf8Bytes("content"));

    await registry.issueCertificate(certId, sha256Hash, 0);
    await expect(registry.issueCertificate(certId, sha256Hash, 0)).to.be.revertedWith(
      "DeepTrustRegistry: certificate already exists"
    );
  });

  it("revokes without deleting the original record", async function () {
    const certId = ethers.keccak256(ethers.toUtf8Bytes("DT-2026-000003"));
    const sha256Hash = ethers.keccak256(ethers.toUtf8Bytes("content"));

    await registry.issueCertificate(certId, sha256Hash, 1 /* MANIPULATED */);
    await registry.revokeCertificate(certId, "Issued in error");

    expect(await registry.isActive(certId)).to.equal(false);
    const record = await registry.getCertificate(certId);
    expect(record.sha256Hash).to.equal(sha256Hash); // original data still intact
  });

  it("rejects revocation from unauthorized callers", async function () {
    const certId = ethers.keccak256(ethers.toUtf8Bytes("DT-2026-000004"));
    const sha256Hash = ethers.keccak256(ethers.toUtf8Bytes("content"));
    await registry.issueCertificate(certId, sha256Hash, 0);

    await expect(
      registry.connect(other).revokeCertificate(certId, "malicious")
    ).to.be.revertedWith("DeepTrustRegistry: not an authorized issuer");
  });
});
