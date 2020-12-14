package co.ledger.wallet.daemon.libledger_core.crypto

import java.security.SecureRandom

import co.ledger.core.RandomNumberGenerator

class SecureRandomRNG extends RandomNumberGenerator {
  private val rng = new SecureRandom()
  override def getRandomBytes(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    rng.nextBytes(bytes)
    bytes
  }
  override def getRandomInt: Int = rng.nextInt()
  override def getRandomLong: Long = rng.nextLong()
  override def getRandomByte: Byte = rng.nextInt().toByte
}
