package co.ledger.wallet.daemon.services

import javax.inject.Singleton

import co.ledger.core.Secp256k1
import io.github.andrebeat.pool._

@Singleton
class ECDSAService extends DaemonService {

  def sign(data: Array[Byte], privKey: Array[Byte]): Array[Byte] = {
    lease { instance => instance.sign(privKey, data) }
  }

  def verify(data: Array[Byte], signature: Array[Byte], publicKey: Array[Byte]): Boolean = {
    lease { instance => instance.verify(data, signature, publicKey) }
  }

  def computePublicKey(privateKey: Array[Byte]): Array[Byte] = {
    lease { instance => instance.computePubKey(privateKey, true) }
  }

  private def lease: Lease[Secp256k1] = _secp256k1Instances.acquire()

  private lazy val _secp256k1Instances: Pool[Secp256k1] = Pool[Secp256k1](Runtime.getRuntime.availableProcessors(), () => Secp256k1.newInstance())
}
