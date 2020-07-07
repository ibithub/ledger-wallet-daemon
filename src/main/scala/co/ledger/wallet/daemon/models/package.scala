package co.ledger.wallet.daemon

package object models {
  type BTCPubKey = Array[Byte]
  type BTCSignature = Array[Byte]
  type BTCSigPub = (BTCSignature, BTCPubKey)
  type ETHSignature = Array[Byte]
  type XRPSignature = Array[Byte]
  type XLMSignature = Array[Byte]
  type XTZSignature = Array[Byte]
}
