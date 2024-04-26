/*
 * Copyright contributors to Hyperledger Besu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.gascalculator;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.BalanceOperation;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.operation.ExpOperation;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;
import org.hyperledger.besu.evm.operation.ExtCodeSizeOperation;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.Keccak256Operation;
import org.hyperledger.besu.evm.operation.LogOperation;
import org.hyperledger.besu.evm.operation.MLoadOperation;
import org.hyperledger.besu.evm.operation.MStore8Operation;
import org.hyperledger.besu.evm.operation.MStoreOperation;
import org.hyperledger.besu.evm.operation.SLoadOperation;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.hyperledger.besu.evm.precompile.ECRECPrecompiledContract;
import org.hyperledger.besu.evm.precompile.IDPrecompiledContract;
import org.hyperledger.besu.evm.precompile.RIPEMD160PrecompiledContract;
import org.hyperledger.besu.evm.precompile.SHA256PrecompiledContract;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Provides various gas cost lookups and calculations used during block processing.
 *
 * <p>The {@code GasCalculator} is meant to encapsulate all Gas-related calculations except for the
 * following "safe" operations:
 *
 * <ul>
 *   <li><b>Operation Gas Deductions:</b> Deducting the operation's gas cost from the VM's current
 *       message frame because the
 * </ul>
 */
public interface GasCalculator {

  // Precompiled Contract Gas Calculations

  /**
   * Returns the gas cost to execute the {@link IDPrecompiledContract}.
   *
   * @param input The input to the ID precompiled contract
   * @return the gas cost to execute the ID precompiled contract
   */
  long idPrecompiledContractGasCost(Bytes input);

  /**
   * Returns the gas cost to execute the {@link ECRECPrecompiledContract}.
   *
   * @return the gas cost to execute the ECREC precompiled contract
   */
  long getEcrecPrecompiledContractGasCost();

  /**
   * Returns the gas cost to execute the {@link SHA256PrecompiledContract}.
   *
   * @param input The input to the SHA256 precompiled contract
   * @return the gas cost to execute the SHA256 precompiled contract
   */
  long sha256PrecompiledContractGasCost(Bytes input);

  /**
   * Returns the gas cost to execute the {@link RIPEMD160PrecompiledContract}.
   *
   * @param input The input to the RIPEMD160 precompiled contract
   * @return the gas cost to execute the RIPEMD160 precompiled contract
   */
  long ripemd160PrecompiledContractGasCost(Bytes input);

  // Gas Tier Lookups

  /**
   * Returns the gas cost for the zero gas tier.
   *
   * @return the gas cost for the zero gas tier
   */
  long getZeroTierGasCost();

  /**
   * Returns the gas cost for the very low gas tier.
   *
   * @return the gas cost for the very low gas tier
   */
  long getVeryLowTierGasCost();

  /**
   * Returns the gas cost for the low gas tier.
   *
   * @return the gas cost for the low gas tier
   */
  long getLowTierGasCost();

  /**
   * Returns the gas cost for the base gas tier.
   *
   * @return the gas cost for the base gas tier
   */
  long getBaseTierGasCost();

  /**
   * Returns the gas cost for the mid gas tier.
   *
   * @return the gas cost for the mid gas tier
   */
  long getMidTierGasCost();

  /**
   * Returns the gas cost for the high gas tier.
   *
   * @return the gas cost for the high gas tier
   */
  long getHighTierGasCost();

  // Call/Create Operation Calculations

  /**
   * Returns the base gas cost to execute a call operation.
   *
   * @return the base gas cost to execute a call operation
   */
  long callOperationBaseGasCost();

  /**
   * Returns the gas cost for one of the various CALL operations.
   *
   * @param frame The current frame
   * @param stipend The gas stipend being provided by the CALL caller
   * @param inputDataOffset The offset in memory to retrieve the CALL input data
   * @param inputDataLength The CALL input data length
   * @param outputDataOffset The offset in memory to place the CALL output data
   * @param outputDataLength The CALL output data length
   * @param transferValue The wei being transferred
   * @param recipient The CALL recipient (may be null if self destructed or new)
   * @param contract The address of the recipient (never null)
   * @return The gas cost for the CALL operation
   * @deprecated use the variant with the `accountIsWarm` parameter.
   */
  @Deprecated(since = "24.2.0", forRemoval = true)
  default long callOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address contract) {
    return callOperationGasCost(
        frame,
        stipend,
        inputDataOffset,
        inputDataLength,
        outputDataOffset,
        outputDataLength,
        transferValue,
        recipient,
        contract,
        true);
  }

  /**
   * Returns the gas cost for one of the various CALL operations.
   *
   * @param frame The current frame
   * @param stipend The gas stipend being provided by the CALL caller
   * @param inputDataOffset The offset in memory to retrieve the CALL input data
   * @param inputDataLength The CALL input data length
   * @param outputDataOffset The offset in memory to place the CALL output data
   * @param outputDataLength The CALL output data length
   * @param transferValue The wei being transferred
   * @param recipient The CALL recipient (may be null if self destructed or new)
   * @param contract The address of the recipient (never null)
   * @param accountIsWarm The address of the contract is "warm" as per EIP-2929
   * @return The gas cost for the CALL operation
   */
  long callOperationGasCost(
      MessageFrame frame,
      long stipend,
      long inputDataOffset,
      long inputDataLength,
      long outputDataOffset,
      long outputDataLength,
      Wei transferValue,
      Account recipient,
      Address contract,
      boolean accountIsWarm);

  /**
   * Gets additional call stipend.
   *
   * @return the additional call stipend
   */
  long getAdditionalCallStipend();

  /**
   * Returns the amount of gas parent will provide its child CALL.
   *
   * @param frame The current frame
   * @param stipend The gas stipend being provided by the CALL caller
   * @param transfersValue Whether call transfers any wei
   * @return the amount of gas parent will provide its child CALL
   */
  long gasAvailableForChildCall(MessageFrame frame, long stipend, boolean transfersValue);

  long completedCreateContractGasCost(final MessageFrame frame);

  /**
   * Returns the amount of gas the CREATE operation will consume.
   *
   * @param frame The current frame
   * @return the amount of gas the CREATE operation will consume
   * @deprecated Compose the operation cost from {@link #txCreateCost()}, {@link
   *     #memoryExpansionGasCost(MessageFrame, long, long)}, and {@link #initcodeCost(int)}
   */
  @Deprecated(since = "24.4.1", forRemoval = true)
  long createOperationGasCost(MessageFrame frame);

  /**
   * Returns the amount of gas the CREATE2 operation will consume.
   *
   * @param frame The current frame
   * @return the amount of gas the CREATE2 operation will consume
   * @deprecated Compose the operation cost from {@link #txCreateCost()}, {@link
   *     #memoryExpansionGasCost(MessageFrame, long, long)}, {@link #createKeccakCost(int)}, and
   *     {@link #initcodeCost(int)}
   */
  @Deprecated(since = "24.4.1", forRemoval = true)
  long create2OperationGasCost(MessageFrame frame);

  /**
   * Returns the base create cost, or TX_CREATE_COST as defined in the execution specs
   *
   * @return the TX_CREATE value for this gas schedule
   */
  long txCreateCost();

  /**
   * For Creates that need to hash the initcode, this is the gas cost for such hashing
   *
   * @param initCodeLength length of the init code, in bytes
   * @return gas cost to charge for hashing
   */
  long createKeccakCost(int initCodeLength);

  /**
   * The cost of a create operation's initcode charge. This is just the initcode cost, separate from
   * the operation base cost and initcode hashing cost.
   *
   * @param initCodeLength Number of bytes in the initcode
   * @return the gas cost for the create initcode
   */
  long initcodeCost(final int initCodeLength);

  default long initcodeStatelessCost(
      final MessageFrame frame, final Address address, final Wei value) {
    return 0;
  }

  /**
   * Returns the amount of gas parent will provide its child CREATE.
   *
   * @param stipend The gas stipend being provided by the CREATE caller
   * @return the amount of gas parent will provide its child CREATE
   */
  long gasAvailableForChildCreate(long stipend);

  // Re-used Operation Calculations

  /**
   * Returns the amount of gas consumed by the data copy operation.
   *
   * @param frame The current frame
   * @param offset The offset in memory to copy the data to
   * @param readSize The length of the data being copied into memory
   * @return the amount of gas consumed by the data copy operation
   */
  long dataCopyOperationGasCost(MessageFrame frame, long offset, long readSize);

  /**
   * Returns the amount of gas consumed by the code copy operation.
   *
   * @param frame The current frame
   * @param memOffset The offset in memory to copy the code to
   * @param codeOffset The starting offset within the code from which to begin copying
   * @param readSize The length of the code being copied into memory
   * @param codeSize The size of the code to copy
   * @return the amount of gas consumed by the code copy operation
   */
  long codeCopyOperationGasCost(
      MessageFrame frame, long memOffset, long codeOffset, long readSize, final long codeSize);

  long pushOperationGasCost(MessageFrame frame, long codeOffset, long readSize, long codeSize);


  long extCodeSizeOperationGasCost(MessageFrame frame);

  /**
   * Returns the cost of expanding memory for the specified access.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access occurs
   * @param length the length of the memory access
   * @return The gas required to expand memory for the specified access
   */
  long memoryExpansionGasCost(MessageFrame frame, long offset, long length);

  // Specific Non-call Operation Calculations

  /**
   * Returns the cost for executing a {@link BalanceOperation}.
   *
   * @return the cost for executing the balance operation
   */
  long getBalanceOperationGasCost(MessageFrame frame);

  /**
   * Returns the cost for executing a {@link BlockHashOperation}.
   *
   * @return the cost for executing the block hash operation
   */
  long getBlockHashOperationGasCost();

  /**
   * Returns the cost for executing a {@link ExpOperation}.
   *
   * @param numBytes The number of bytes for the exponent parameter
   * @return the cost for executing the exp operation
   */
  long expOperationGasCost(int numBytes);

  /**
   * Returns the cost for executing a {@link ExtCodeCopyOperation}.
   *
   * @param frame The current frame
   * @param address The address to use for the gas cost computation
   * @param memOffset The offset in memory to external code copy the data to
   * @param codeOffset The starting offset within the code from which to begin copying
   * @param readSize The length of the code being copied into memory
   * @param codeSize The size of the code to copy
   * @return the cost for executing the external code size operation
   */
  long extCodeCopyOperationGasCost(
      MessageFrame frame,
      final Address address,
      long memOffset,
      long codeOffset,
      long readSize,
      final long codeSize);

  /**
   * Returns the cost for executing a {@link ExtCodeHashOperation}.
   *
   * @return the cost for executing the external code hash operation
   */
  long extCodeHashOperationGasCost(final MessageFrame frame, Optional<Address> address);

  /**
   * Returns the cost for executing a {@link ExtCodeSizeOperation}.
   *
   * @return the cost for executing the external code size operation
   */
  long getExtCodeSizeOperationGasCost();

  /**
   * Returns the cost for executing a {@link JumpDestOperation}.
   *
   * @return the cost for executing the jump destination operation
   */
  long getJumpDestOperationGasCost();

  /**
   * Returns the cost for executing a {@link LogOperation}.
   *
   * @param frame The current frame
   * @param dataOffset The offset in memory where the log data exists
   * @param dataLength The length of the log data to read from memory
   * @param numTopics The number of topics in the log
   * @return the cost for executing the external code size operation
   */
  long logOperationGasCost(MessageFrame frame, long dataOffset, long dataLength, int numTopics);

  /**
   * Returns the cost for executing a {@link MLoadOperation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory load operation
   */
  long mLoadOperationGasCost(MessageFrame frame, long offset);

  /**
   * Returns the cost for executing a {@link MStoreOperation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory store operation
   */
  long mStoreOperationGasCost(MessageFrame frame, long offset);

  /**
   * Returns the cost for executing a {@link MStore8Operation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory byte store operation
   */
  long mStore8OperationGasCost(MessageFrame frame, long offset);

  /**
   * Returns the cost for executing a {@link SelfDestructOperation}.
   *
   * @param frame The current frame
   * @param recipient The recipient of the self destructed inheritance (may be null)
   * @param recipientAddress The address of the self destructed account
   * @param inheritance The amount the recipient will receive
   * @param originatorAddress The address of the self destructing account
   * @return the cost for executing the self destruct operation
   */
  long selfDestructOperationGasCost(
      MessageFrame frame,
      Account recipient,
      final Address recipientAddress,
      Wei inheritance,
      Address originatorAddress);

  /**
   * Returns the cost for executing a {@link Keccak256Operation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the data to be hashed exists
   * @param length The hashed data length
   * @return the cost for executing the memory byte store operation
   */
  long keccak256OperationGasCost(MessageFrame frame, long offset, long length);

  /**
   * Returns the cost for executing a {@link SLoadOperation}.
   *
   * @return the cost for executing the storage load operation
   */
  long getSloadOperationGasCost(MessageFrame frame, UInt256 key);

  /**
   * Returns the cost for an SSTORE operation.
   *
   * @param frame the current frame
   * @param key the slot key
   * @param newValue the new value to be stored
   * @param currentValue the supplier of the current value
   * @param originalValue the supplier of the original value
   * @return the gas cost for the SSTORE operation
   */
  long calculateStorageCost(
      MessageFrame frame,
      UInt256 key,
      UInt256 newValue,
      Supplier<UInt256> currentValue,
      Supplier<UInt256> originalValue);

  /**
   * Returns the refund amount for an SSTORE operation.
   *
   * @param newValue the new value to be stored
   * @param currentValue the supplier of the current value
   * @param originalValue the supplier of the original value
   * @return the gas refund for the SSTORE operation
   */
  long calculateStorageRefundAmount(
      UInt256 newValue, Supplier<UInt256> currentValue, Supplier<UInt256> originalValue);

  /**
   * Returns the refund amount for deleting an account in a {@link SelfDestructOperation}.
   *
   * @return the refund amount for deleting an account in a self destruct operation
   */
  long getSelfDestructRefundAmount();

  /**
   * Returns the cost of a SLOAD to a storage slot not previously loaded in the TX context.
   *
   * @return the cost of a SLOAD to a storage slot not previously loaded in the TX context.
   */
  default long getColdSloadCost() {
    return 0L;
  }

  /**
   * Returns the cost to access an account not previously accessed in the TX context.
   *
   * @return the cost to access an account not previously accessed in the TX context.
   */
  default long getColdAccountAccessCost() {
    return 0L;
  }

  /**
   * Returns the cost of a SLOAD to a storage slot that has previously been loaded in the TX
   * context.
   *
   * @return the cost of a SLOAD to a storage slot that has previously been loaded in the TX
   *     context.
   */
  default long getWarmStorageReadCost() {
    return 0L;
  }

  /**
   * For the purposes of this gas calculator, is this address a precompile?
   *
   * @param address the address to test for being a precompile
   * @return true if it is a precompile.
   */
  default boolean isPrecompile(final Address address) {
    return false;
  }

  /**
   * Mod exp gas cost.
   *
   * @param input the input
   * @return the long
   */
  default long modExpGasCost(final Bytes input) {
    return 0L;
  }

  /**
   * Returns the cost for a {@link AbstractMessageProcessor} to deposit the code in storage
   *
   * @param frame The current frame
   * @param codeSize The size of the code in bytes
   * @return the code deposit cost
   */
  long codeDepositGasCost(MessageFrame frame, int codeSize);

  /**
   * Returns the intrinsic gas cost of a transaction payload, i.e. the cost deriving from its
   * encoded binary representation when stored on-chain.
   *
   * @param transactionPayload The encoded transaction, as bytes
   * @param isContractCreate Is this transaction a contract creation transaction?
   * @return the transaction's intrinsic gas cost
   */
  long transactionIntrinsicGasCost(Bytes transactionPayload, boolean isContractCreate);

  /**
   * Returns the gas cost of the explicitly declared access list.
   *
   * @param accessListEntries The access list entries
   * @return the access list's gas cost
   */
  default long accessListGasCost(final List<AccessListEntry> accessListEntries) {
    return accessListGasCost(
        accessListEntries.size(),
        accessListEntries.stream().mapToInt(e -> e.storageKeys().size()).sum());
  }

  /**
   * Returns the gas cost of the explicitly declared access list.
   *
   * @param addresses The count of addresses accessed
   * @param storageSlots The count of storage slots accessed
   * @return the access list's gas cost
   */
  default long accessListGasCost(final int addresses, final int storageSlots) {
    return 0L;
  }

  /**
   * A measure of the maximum amount of refunded gas a transaction will be credited with.
   *
   * @return the quotient of the equation `txGasCost / refundQuotient`.
   */
  default long getMaxRefundQuotient() {
    return 2;
  }

  /**
   * Maximum Cost of a Transaction of a certain length.
   *
   * @param size the length of the transaction, in bytes
   * @return the maximum gas cost
   */
  // what would be the gas for a PMT with hash of all non-zeros
  long getMaximumTransactionCost(int size);

  /**
   * Minimum gas cost of a transaction.
   *
   * @return the minimum gas cost
   */
  long getMinimumTransactionCost();

  /**
   * Returns the cost of a loading from Transient Storage
   *
   * @return the cost of a TLOAD from a storage slot
   */
  default long getTransientLoadOperationGasCost() {
    return 0L;
  }

  /**
   * Returns the cost of a storing to Transient Storage
   *
   * @return the cost of a TSTORE to a storage slot
   */
  default long getTransientStoreOperationGasCost() {
    return 0L;
  }

  /**
   * Return the gas cost given the number of blobs
   *
   * @param blobCount the number of blobs
   * @return the total gas cost
   */
  default long blobGasCost(final int blobCount) {
    return 0L;
  }

  /**
   * Compute the new value for the excess blob gas, given the parent value and the count of new
   * blobs
   *
   * @param parentExcessBlobGas excess blob gas from the parent
   * @param newBlobs count of new blobs
   * @return the new excess blob gas value
   */
  default long computeExcessBlobGas(final long parentExcessBlobGas, final int newBlobs) {
    return 0L;
  }

  /**
   * Compute the new value for the excess blob gas, given the parent value and the blob gas used
   *
   * @param parentExcessBlobGas excess blob gas from the parent
   * @param blobGasUsed blob gas used
   * @return the new excess blob gas value
   */
  default long computeExcessBlobGas(final long parentExcessBlobGas, final long blobGasUsed) {
    return 0L;
  }

  /**
   * Compute access events cost of a transaction
   *
   * @param transaction transaction
   * @return gas cost after computing all access events
   */
  default long computeBaseAccessEventsCost(
      final AccessWitness accessWitness,
      final Transaction transaction,
      final MutableAccount sender) {
    return 0L;
  }

}
