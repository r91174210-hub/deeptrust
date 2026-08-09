require("@nomicfoundation/hardhat-toolbox");

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: { enabled: true, runs: 200 }
    }
  },
  networks: {
    ganache: {
      url: "http://127.0.0.1:8545",
      // Never commit real private keys. For local Ganache this reads a
      // pre-funded dev account from env; production networks must use a
      // secrets manager / hardware wallet signer instead.
      accounts: process.env.GANACHE_PRIVATE_KEY ? [process.env.GANACHE_PRIVATE_KEY] : []
    }
  }
};
