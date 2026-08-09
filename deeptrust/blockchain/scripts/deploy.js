const hre = require("hardhat");

async function main() {
  const DeepTrustRegistry = await hre.ethers.getContractFactory("DeepTrustRegistry");
  const registry = await DeepTrustRegistry.deploy();
  await registry.waitForDeployment();

  const address = await registry.getAddress();
  console.log("DeepTrustRegistry deployed to:", address);
  console.log("Copy this address into backend application.yml as deeptrust.blockchain.contract-address");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
