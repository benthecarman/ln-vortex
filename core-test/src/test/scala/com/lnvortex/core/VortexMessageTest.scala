package com.lnvortex.core

import com.lnvortex.core.crypto.{BlindSchnorrUtil, BlindingTweaks}
import org.bitcoins.core.currency._
import org.bitcoins.core.protocol.script.P2WSHWitnessSPKV0
import org.bitcoins.core.protocol.transaction.TransactionOutput
import org.bitcoins.crypto._
import org.bitcoins.testkitcore.gen.ScriptGenerators
import org.bitcoins.testkitcore.util.BitcoinSUnitTest

class VortexMessageTest extends BitcoinSUnitTest {

  val privKey: ECPrivateKey = ECPrivateKey.freshPrivateKey
  val pubKey: SchnorrPublicKey = privKey.schnorrPublicKey
  val kVal: ECPrivateKey = ECPrivateKey.freshPrivateKey
  val nonce: SchnorrNonce = kVal.schnorrNonce

  val tweaks: BlindingTweaks =
    BlindingTweaks.freshBlindingTweaks(pubKey, nonce)
  val amount: CurrencyUnit = Bitcoins.one

  it must "correctly verify a Bob message" in {
    forAll(ScriptGenerators.p2wshSPKV0.map(_._1)) { spk =>
      val output = TransactionOutput(amount, spk)
      val hash = CryptoUtil.sha256(output.bytes).bytes

      val challenge =
        BlindSchnorrUtil.generateChallenge(pubKey, nonce, tweaks, hash)

      val blindSig = BlindSchnorrUtil.generateBlindSig(privKey, kVal, challenge)

      val sig =
        BlindSchnorrUtil.unblindSignature(blindSig, pubKey, nonce, tweaks, hash)

      val bobMsg = BobMessage(sig, output)

      val verify = bobMsg.verifySigAndOutput(pubKey)

      assert(verify)
    }
  }

  it must "fail to verify a Bob message with non p2wsh spks" in {
    forAll(
      ScriptGenerators.scriptPubKey
        .map(_._1)
        .suchThat(!_.isInstanceOf[P2WSHWitnessSPKV0])) { spk =>
      val output = TransactionOutput(amount, spk)
      val hash = CryptoUtil.sha256(output.bytes).bytes

      val challenge =
        BlindSchnorrUtil.generateChallenge(pubKey, nonce, tweaks, hash)

      val blindSig = BlindSchnorrUtil.generateBlindSig(privKey, kVal, challenge)

      val sig =
        BlindSchnorrUtil.unblindSignature(blindSig, pubKey, nonce, tweaks, hash)

      val bobMsg = BobMessage(sig, output)

      val verify = bobMsg.verifySigAndOutput(pubKey)

      assert(!verify)
    }
  }

  it must "fail to verify a Bob message with different tweaks sig" in {
    forAll(ScriptGenerators.p2wshSPKV0.map(_._1)) { spk =>
      val output = TransactionOutput(amount, spk)
      val hash = CryptoUtil.sha256(output.bytes).bytes

      val challenge =
        BlindSchnorrUtil.generateChallenge(pubKey, nonce, tweaks, hash)

      val blindSig = BlindSchnorrUtil.generateBlindSig(privKey, kVal, challenge)

      val newTweaks = BlindingTweaks.freshBlindingTweaks(pubKey, nonce)

      assertThrows[IllegalArgumentException](
        BlindSchnorrUtil.unblindSignature(blindSig = blindSig,
                                          signerPubKey = pubKey,
                                          signerNonce = nonce,
                                          blindingTweaks = newTweaks,
                                          message = hash))
    }
  }

  it must "fail to verify a Bob message with wrong keys" in {
    forAll(ScriptGenerators.p2wshSPKV0.map(_._1)) { spk =>
      val output = TransactionOutput(amount, spk)
      val hash = CryptoUtil.sha256(output.bytes).bytes

      val challenge =
        BlindSchnorrUtil.generateChallenge(pubKey, nonce, tweaks, hash)

      val blindSig = BlindSchnorrUtil.generateBlindSig(privKey, kVal, challenge)

      val sig =
        BlindSchnorrUtil.unblindSignature(blindSig, pubKey, nonce, tweaks, hash)

      val bobMsg = BobMessage(sig, output)

      val verify = bobMsg.verifySigAndOutput(kVal.schnorrPublicKey)

      assert(!verify)
    }
  }

}
