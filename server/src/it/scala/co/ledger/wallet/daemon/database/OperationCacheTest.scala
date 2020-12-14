package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.exceptions.OperationNotFoundException
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class OperationCacheTest extends AssertionsForJUnit {
  private val operationCache: OperationCache = new OperationCache()
  import operationCache._

  @Test def verifyAccountOperationNextUnique(): Unit = {
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, previous)
    assert(record === operationCache.getPreviousOperationRecord(id))
    val original = operationCache.insertOperation(id, 1L, "myWallet", 1, 2, 20, next, previous)
    assert(record === original)
  }

  @Test def verifyGetOperationCandidates(): Unit = {
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, previous)
    val nextRecord = operationCache.getOperationCandidate(next.get)
    val insertedNext = insertRecord(nextRecord)
    assert(insertedNext != nextRecord)
    assert(insertedNext.id === nextRecord.id)
    assert(nextRecord.offset() === insertedNext.offset())
    assert(nextRecord.next === insertedNext.next)
    val nextNextRecord = operationCache.getOperationCandidate(nextRecord.next.get)
    assert(nextNextRecord.id === nextRecord.next.get)
  }

  @Test def verifyGetPreviousOperations(): Unit = {
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, None)
    val nextRecord = insertRecord(operationCache.getOperationCandidate(next.get))
    val previousOfNext = operationCache.getPreviousOperationRecord(nextRecord.previous.get)
    assert(previousOfNext === record)
  }

  @Test def verifyDeletePool(): Unit = {
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, None)
    assert(record.offset() === 0)
    operationCache.updateOffset(1L, "myWallet", 1)
    val updatedRecord = operationCache.getPreviousOperationRecord(next.get)
    assert(updatedRecord.id === record.id)
    assert(updatedRecord.offset() === 1)

    val sameRecord = operationCache.getPreviousOperationRecord(id)
    assert(sameRecord === updatedRecord)

    val nextRecord = operationCache.getOperationCandidate(next.get)
    assert(nextRecord.id === next.get)
    assert(nextRecord.offset() === updatedRecord.offset() + updatedRecord.batch)

    operationCache.deleteOperations(1L)
    try {
      operationCache.getPreviousOperationRecord(next.get)
      fail()
    } catch {
      case _: OperationNotFoundException => // expected
    }
  }

  private def insertRecord(record: AtomicRecord) = {
    operationCache.insertOperation(
      record.id,
      record.poolId,
      record.walletName.get,
      record.accountIndex.get,
      record.offset(),
      record.batch,
      record.next,
      record.previous)
  }
}
