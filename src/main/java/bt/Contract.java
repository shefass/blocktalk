package bt;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;

import bt.compiler.Compiler;
import burst.kit.crypto.BurstCrypto;

/**
 * The BlockTalk smart contract abstract class.
 * 
 * Users should extend this class and implement at least the
 * {@link #txReceived()} method.
 * 
 * The class should be public but please be extremely careful when setting a
 * method public. Public methods can be called from external sources in the
 * blockchain (by incoming transactions of other accounts or contracts).
 * Further, public methods should not return values and should take at most 3
 * arguments.
 * 
 * @author jjos
 *
 */
public abstract class Contract {

	public final static long ONE_BURST = 100000000L;
	public final static long FEE_QUANT = 735000L;
	public final static long STEP_FEE = FEE_QUANT / 10L; // After AT2 fork, othewise ONE_BURST/10

	Address address;
	Address creator;
	Timestamp creation;

	Transaction currentTx;
	long activationFee;

	// Thread stuff to emulate sleep functions
	Semaphore semaphore = new Semaphore(1);
	boolean running;
	Timestamp sleepUntil;

	protected Contract() {
		Emulator emu = Emulator.getInstance();
		setInitialVars(emu.curTx.getSenderAddress(), new Timestamp(emu.getCurrentBlock().getHeight(), 0),
				emu.curTx.getAmount());
	}

	/**
	 * Utility function that return the address of a given BURST-account
	 * 
	 * @param account
	 * @return
	 */
	protected Address parseAddress(String rs) {
		return Emulator.getInstance().getAddress(rs);
	}

	/**
	 * Send the entire balance to the given address.
	 * 
	 * Care should be execised with this function since a contract with no balance
	 * cannot continue to run!
	 * 
	 * @param ad the address
	 */
	protected void sendBalance(Address ad) {
		sendAmount(this.address.balance, ad);
	}

	/**
	 * Send the given amount to the receiver address
	 * 
	 * @param amount
	 * @param receiver
	 */
	protected void sendAmount(long amount, Address receiver) {
		Emulator.getInstance().send(address, receiver, amount);
	}

	/**
	 * Send the given message to the given address.
	 * 
	 * The message has the isText flag set as false, the hexadecimal values are
	 * converted to characters when shown in BRS wallet. Message is always
	 * unencrypted.
	 * 
	 * @param message  the message, truncated in 4*sizeof(long)
	 * @param amount   the amount or 0 to send the message only
	 * @param receiver the address
	 */
	protected void sendMessage(String message, Address receiver) {
		Emulator.getInstance().send(address, receiver, 0, message);
	}

	/**
	 * Send the given message to the given address.
	 * 
	 * The message has the isText flag set as false, the hexadecimal values are
	 * converted to characters when shown in BRS wallet. Message is always
	 * unencrypted.
	 * 
	 * @param message  the message in form of a 4 longs register
	 * @param receiver the address
	 */
	protected void sendMessage(Register message, Address receiver) {
		Emulator.getInstance().send(address, receiver, 0, message);
	}

	/**
	 * Function to be called before processing the transactions of the current
	 * block.
	 * 
	 * Users should overload this function if there is some action to be taken
	 * before processing the first {@link #txReceived()} for the current block.
	 * 
	 * Use this function carefully, since #{@link #getCurrentTx()} and similar
	 * functions are still unavalable inside this function.
	 */
	protected void blockStarted() {
	}

	/**
	 * Function to be called when all transactions on the current block were
	 * processed.
	 * 
	 * Users should overload this function if there is some action to be taken after
	 * the last {@link #txReceived()} was called for the current block.
	 */
	protected void blockFinished() {
	}

	/**
	 * Get the first transaction received after the given timestamp
	 * 
	 * @param ts
	 * @return
	 */
	protected Transaction getTxAfterTimestamp(Timestamp ts) {
		return Emulator.getInstance().getTxAfter(address, ts);
	}

	/**
	 * @return the transaction being processed at this moment
	 */
	protected Transaction getCurrentTx() {
		return currentTx;
	}

	/**
	 * @return the current transaction timestamp
	 */
	protected Timestamp getCurrentTxTimestamp() {
		return getCurrentTx().getTimestamp();
	}

	/**
	 * @return the current transaction sender address
	 */
	protected Address getCurrentTxSender() {
		return getCurrentTx().getSenderAddress();
	}

	/**
	 * @return the current transaction amount
	 */
	protected long getCurrentTxAmount() {
		return getCurrentTx().getAmount();
	}

	/**
	 * @return the block hash of the previous block (part 1 of 4)
	 */
	protected Register getPrevBlockHash() {
		return Emulator.getInstance().getPrevBlock().hash;
	}

	/**
	 * @return the first part of the previous block hash
	 */
	protected long getPrevBlockHash1() {
		return Emulator.getInstance().getPrevBlock().hash.getValue1();
	}

	/**
	 * @return the timestamp of the previous block
	 */
	protected Timestamp getPrevBlockTimestamp() {
		return new Timestamp(Emulator.getInstance().getPrevBlock().getHeight(), 0);
	}

	/**
	 * @return the timestamp of the block being processed
	 */
	protected Timestamp getBlockTimestamp() {
		return new Timestamp(Emulator.getInstance().getCurrentBlock().getHeight(), 0);
	}

	/**
	 * @return the address of the creator of this contract
	 */
	protected Address getCreator() {
		return creator;
	}

	/**
	 * @return the creation timestamp of this contract
	 */
	protected Timestamp getCreationTimestamp() {
		return creation;
	}

	/**
	 * @return the current balance of this contract
	 */
	protected long getCurrentBalance() {
		return address.balance;
	}

	@EmulatorWarning
	public static Register performSHA256_(Register input) {
		Register ret = new Register();

		ByteBuffer b = ByteBuffer.allocate(32);
		b.order(ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < 4; i++) {
			b.putLong(input.value[i]);
		}

		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			ByteBuffer shab = ByteBuffer.wrap(sha256.digest(b.array()));
			shab.order(ByteOrder.LITTLE_ENDIAN);

			for (int i = 0; i < 4; i++) {
				ret.value[i] = shab.getLong(i * 8);
			}
		} catch (NoSuchAlgorithmException e) {
			// not expected to reach that point
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * @return a SHA256 hash of the given input
	 */
	protected Register performSHA256(Register input) {
		return performSHA256_(input);
	}

	/**
	 * A utility function returning only the first 64 bits of a SHA256 for two long
	 * inputs.
	 * 
	 * @return the first 64 bits SHA256 hash of the given input
	 */
	protected long performSHA256_64(long input1, long input2) {
		Register input = new Register();
		input.value[0] = input1;
		input.value[1] = input2;

		Register ret = performSHA256_(input);
		return ret.getValue1();
	}

	/**
	 * Sleeps until the next block.
	 */
	protected void sleepOneBlock() {
		sleep(0);
	}

	/**
	 * Sleeps for the given number of blocks.
	 * 
	 * @param nblocks number of blocks to sleep
	 */
	protected void sleep(long nblocks) {
		if(nblocks <= 0)
			sleepUntil = null;
		else
			sleepUntil = new Timestamp(Emulator.getInstance().getCurrentBlock().height + nblocks, 0);
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		sleepUntil = null;
	}

	/**
	 * A new transaction was received.
	 * 
	 * Overload this function to implement your contract.
	 */
	public abstract void txReceived();

	// ========================================================
	// Functions not visible, will be used by the virtual
	// machine when in debug mode but not exported to the
	// AT blockchain machine code

	void setInitialVars(Address creator, Timestamp creation, long activationFee) {
		this.creator = creator;
		this.creation = creation;
		this.activationFee = activationFee;
	}

	void setCurrentTx(Transaction current) {
		this.currentTx = current;
	}

	@EmulatorWarning
	public String getFieldValues() {
		String ret = "<html>";
		Field[] fields = this.getClass().getDeclaredFields();
		for (Field f : fields) {
			try {
				f.setAccessible(true);
				Object v = f.get(this);
				ret += "<b>" + f.getName() + "</b> = " + (v != null ? v.toString() : "null") + "<br>";
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	/**
	 * This method should **NOT** be called except from in a main() method for
	 * convenience. Class detection taken from
	 * javafx.application.Application::launch
	 * 
	 * @return The AT code byte
	 */
	@EmulatorWarning
	protected static byte[] compile() {
		// Figure out the right class to call
		StackTraceElement[] cause = Thread.currentThread().getStackTrace();

		boolean foundThisMethod = false;
		String callingClassName = null;
		for (StackTraceElement se : cause) {
			// Skip entries until we get to the entry for this class
			String className = se.getClassName();
			String methodName = se.getMethodName();
			if (foundThisMethod) {
				callingClassName = className;
				break;
			} else if (Contract.class.getName().equals(className) && "compile".equals(methodName)) {
				foundThisMethod = true;
			}
		}

		if (callingClassName == null) {
			throw new RuntimeException("Error: unable to determine contract class");
		}

		try {
			Class theClass = Class.forName(callingClassName, false, Thread.currentThread().getContextClassLoader());
			if (Contract.class.isAssignableFrom(theClass)) {
				// noinspection unchecked
				byte[] code = compile(theClass);
				if (code != null)
					System.out.println("Compiled AT bytecode: " + BurstCrypto.getInstance().toHexString(code));
				return code;
			} else {
				throw new RuntimeException("Error: " + theClass + " is not a subclass of bt.Contract");
			}
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static byte[] compile(Class<? extends Contract> clazz) throws IOException {
		Compiler compiler = new Compiler(clazz);
		compiler.compile();
		if (compiler.getErrors().size() > 0)
			return null;

		compiler.link();
		return compiler.getCode();
	}
}
