package sos;

import java.util.Collections;
import java.util.ListIterator;
import java.util.Random;
import java.util.Vector;

//import sos.SOS.DeviceInfo;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 * 
 */

public class SOS implements CPU.TrapHandler {
	// ======================================================================
	// Member variables
	// ----------------------------------------------------------------------

	/**
	 * This flag causes the SOS to print lots of potentially helpful status
	 * messages
	 **/
	public static final boolean m_verbose = true;

	/**
	 * The CPU the operating system is managing.
	 **/
	private CPU m_CPU = null;

	/**
	 * The RAM attached to the CPU.
	 **/
	private RAM m_RAM = null;

	/**
	 * A reference to the PCB which contains the current process
	 */
	private ProcessControlBlock m_currProcess;

	/**
	 * Vector of devices installed on the computer
	 */
	private Vector<DeviceInfo> m_devices;

	/**
	 * Vector of all available programs on the OS
	 */
	private Vector<Program> m_programs = new Vector<Program>();

	/**
	 * Specifies process ID for next process loaded. Should be incremented every
	 * time used
	 */
	private int m_nextProcessID = 1001;

	/**
	 * A list of all currently loaded processes in states Ready, Running or
	 * Blocked
	 */
	private Vector<ProcessControlBlock> m_processes = new Vector<ProcessControlBlock>();

	/**
	 * A vector of MemBlock objects
	 */
	private Vector<MemBlock> m_freeList = new Vector<MemBlock>();

	/*
	 * ======================================================================
	 * Constructors & Debugging
	 * ----------------------------------------------------------------------
	 */

	// These constants define the system calls this OS can currently handle
	public static final int SYSCALL_EXIT = 0; /* exit the current program */
	public static final int SYSCALL_OUTPUT = 1; /* outputs a number */
	public static final int SYSCALL_GETPID = 2; /* get current process id */
	public static final int SYSCALL_OPEN = 3; /* access a device */
	public static final int SYSCALL_CLOSE = 4; /* release a device */
	public static final int SYSCALL_READ = 5; /* get input from device */
	public static final int SYSCALL_WRITE = 6; /* send output to device */
	public static final int SYSCALL_EXEC = 7; /* spawn a new process */
	public static final int SYSCALL_YIELD = 8; /*
												 * yield the CPU to another
												 * process
												 */
	public static final int SYSCALL_COREDUMP = 9; /* print process state and exit */

	// Define the success code value
	public static final int SUCCESS_CODE = 0;

	// These constants define the error code values from syscalls
	public static final int ERROR_NO_PROCESSES = -8;
	public static final int ERROR_DEVICE_EXISTENCE = -2;
	public static final int ERROR_DEVICE_NOT_USABLE = -3;
	public static final int ERROR_DEVICE_OPEN = -4;
	public static final int ERROR_DEVICE_NOT_OPEN = -5;
	public static final int ERROR_DEVICE_NOT_READABLE = -6;
	public static final int ERROR_DEVICE_NOT_WRITEABLE = -7;
	public static final int ERROR_NEED_MORE_SPACE = -9;

	/** This process is used as the idle process' id */
	public static final int IDLE_PROC_ID = 999;

	/**
	 * The constructor does nothing special
	 */
	public SOS(CPU c, RAM r) {
		// Init member list
		m_CPU = c;
		m_RAM = r;
		m_CPU.registerTrapHandler(this);
		m_currProcess = null;
		m_devices = new Vector<DeviceInfo>(0);
		m_freeList.add(new MemBlock(0, m_RAM.getSize()));
	}// SOS ctor

	/**
	 * Does a System.out.print as long as m_verbose is true
	 **/
	public static void debugPrint(String s) {
		if (m_verbose) {
			System.out.print(s);
		}
	}

	/**
	 * Does a System.out.println as long as m_verbose is true
	 **/
	public static void debugPrintln(String s) {
		if (m_verbose) {
			System.out.println(s);
		}
	}

	/*
	 * ======================================================================
	 * Memory Block Management Methods
	 * ----------------------------------------------------------------------
	 */

	/**
	 * allocBlock
	 * 
	 * Finds a free RAM block that is closest in size to the size of the process
	 * 	Essentially a best fit allocation algorithm
	 * 
	 * @param size of the process
	 * @return the address to place the process at, or -1 if there is no room
	 */
	private int allocBlock(int size) {
		
		//Will hold MemBlocks in which a process of given size could fit
		Vector<MemBlock> bigEnough = new Vector<MemBlock>();
		
		int totalAvailableSpace = 0;
 		
		//Find the MemBlocks that could fit the process we want to create
		for(MemBlock mb : m_freeList)
		{
			if(size < mb.getSize())
			{
				bigEnough.add(mb);
			}
		
			totalAvailableSpace += mb.m_size;
		}
		
		//The memory block that will be closest in size to the prog size
		MemBlock smallest = null;
		if(!bigEnough.isEmpty())
		{
			//Smallest process size found
			int littleProc = bigEnough.elementAt(0).m_size;
			
			//Find the memory block which is closest in size to the given size
			for(MemBlock mb : bigEnough)
			{	
				if(mb.m_size <= littleProc)
				{
					littleProc = mb.m_size;
					smallest = mb;
				}
			}
		}
		//If there are no MemBlocks which will fit the process
		else
		{	
			//Check if it is worth defragmenting
			if(totalAvailableSpace > size)
			{
				Collections.sort(m_freeList);
				
				int nextBase = compactAllocBlocks(); 				//Defragment RAM
				
				//After defrag, add a new free memory block that is from where the lim of 
				//	new proc will be to top of RAM
				MemBlock toInsert = new MemBlock(nextBase + size + 1, m_RAM.getSize() - (nextBase + size));
				
				//Resize that MemBlock if it is too large
				if(toInsert.m_addr + toInsert.m_size > 3000)
				{
					toInsert.m_size = m_RAM.getSize() - toInsert.m_addr;
				}
				
				m_freeList.clear();
				m_freeList.add(toInsert);
				return nextBase;
			}
			else
				return -1;
		}//else
		
		if(smallest != null)
		{
			//New MemBlock which is the remaining free space after process is placed there
			MemBlock toInsert = new MemBlock(smallest.m_addr + size + 1, smallest.m_size - size);
			if(toInsert.m_addr + toInsert.m_size > 3000)
			{
				toInsert.m_size = m_RAM.getSize() - toInsert.m_addr;
			}
			m_freeList.remove(smallest);
			m_freeList.add(toInsert);
			return smallest.m_addr;
		}
		
		return -1;
	}// allocBlock
	
	/**
	 * compactAllocBlocks
	 * 
	 * Moves all processes to be contiguous in RAM so that more processes can fit
	 * 	*DEFRAGMENTER*
	 * 
	 */
	public int compactAllocBlocks()
	{
		int nextBase = 0;
		
		int j = 0;
		
		Collections.sort(m_processes);
		
		while(j < m_processes.size())
		{
			//If the base of this process is not as low as it can be in RAM address
			if(m_processes.elementAt(j).registers[m_CPU.BASE] != nextBase)
			{
				m_processes.elementAt(j).move(nextBase);
			}
			//Update next base to limit of the process at index j
			nextBase = m_processes.elementAt(j).registers[m_CPU.LIM] + 1;
			
			j++;
		}// while
		
		//if compaction occured, return where the next process will be added in RAM
		if(nextBase != 0)
		{
			return nextBase;
		}
		//If compaction fails
		return -1;
	}//compactAllocBlocs

	/**
	 * freeCurrProcessMemBlock
	 * 
	 * When a process is killed, redefine that process' space as free
	 * 	Merges any contiguous free memory blocks
	 */
	private void freeCurrProcessMemBlock() 
	{
		int curBase = m_currProcess.registers[m_CPU.BASE];
		int curLim = m_currProcess.registers[m_CPU.LIM];
	
		//Add a new free block where the process that is being removed is
		MemBlock newBlock = new MemBlock(curBase, curLim - curBase);
		m_freeList.add(newBlock);

		Collections.sort(m_freeList);
		
		MemBlock belowBlock = null;
		MemBlock aboveBlock = null;
		
		//Iterate through the list of MemBlocks and merge the adjacent blocks
		for(int i = 0; i < m_freeList.size() ; ++i)
		{
			MemBlock curBlock = m_freeList.elementAt(i);
			
			//Reset to null each iteration to recheck for adjacency
			belowBlock = null;
			aboveBlock = null;
			
			if(i != 0)
				belowBlock = m_freeList.elementAt(i - 1);
			
			if(i != (m_freeList.size() -1))
				aboveBlock = m_freeList.elementAt(i + 1);
			
			//Addresses for reading/coding ease
			int aboveBlockBase = 0;
			int belowBlockBase = 0;
			int belowBlockLim = 0;
			int aboveBlockLim = 0;
			int curBlockLim = curBlock.m_addr + curBlock.m_size;
			
			if(belowBlock != null)
			{
				belowBlockBase = belowBlock.m_addr;
				belowBlockLim = belowBlock.m_size + belowBlockBase;
			}
			
			if(aboveBlock != null)
			{
				aboveBlockBase = aboveBlock.m_addr;
				aboveBlockLim = aboveBlock.m_size + aboveBlockBase;
			}
			
			
			//The MemBlock that will be added to the freeList
			MemBlock tempBlock = null;
			

			//If the block below is adjacent
			if(belowBlockLim + 1 == curBlock.m_addr && aboveBlockBase - 1 != curBlockLim)
			{
				tempBlock = new MemBlock(belowBlockBase, curBlockLim - belowBlockBase);
				m_freeList.add(tempBlock);
				m_freeList.remove(belowBlock);
				m_freeList.remove(curBlock);
			}
		
			//If the block above is adjacent
			else if(belowBlockLim + 1 != curBlock.m_addr && aboveBlockBase - 1 == curBlockLim)
			{
				tempBlock = new MemBlock(curBlock.m_addr, aboveBlockLim - curBlock.m_addr);
				m_freeList.add(tempBlock);
				m_freeList.remove(aboveBlock);
				m_freeList.remove(curBlock);
			}
			
			//If the blocks below and above are adjacent to current memblock
			else if(belowBlockLim + 1 == curBlock.m_addr && aboveBlockBase - 1 == curBlockLim)
			{
				tempBlock = new MemBlock(belowBlockBase, aboveBlockLim - belowBlockBase);
				m_freeList.add(tempBlock);
				m_freeList.remove(belowBlock);
				m_freeList.remove(aboveBlock);
				m_freeList.remove(curBlock);
			}
		}
	}// freeCurrProcessMemBlock

	/**
	 * printMemAlloc *DEBUGGING*
	 * 
	 * outputs the contents of m_freeList and m_processes to the console and
	 * performs a fragmentation analysis. It also prints the value in RAM at the
	 * BASE and LIMIT registers. This is useful for tracking down errors related
	 * to moving process in RAM.
	 * 
	 * SIDE EFFECT: The contents of m_freeList and m_processes are sorted.
	 * 
	 */
	private void printMemAlloc() {
		// If verbose mode is off, do nothing
		if (!m_verbose)
			return;

		// Print a header
		System.out
				.println("\n----------========== Memory Allocation Table ==========----------");

		// Sort the lists by address
		Collections.sort(m_processes);
		Collections.sort(m_freeList);

		// Initialize references to the first entry in each list
		MemBlock m = null;
		ProcessControlBlock pi = null;
		ListIterator<MemBlock> iterFree = m_freeList.listIterator();
		ListIterator<ProcessControlBlock> iterProc = m_processes.listIterator();
		if (iterFree.hasNext())
			m = iterFree.next();
		if (iterProc.hasNext())
			pi = iterProc.next();

		// Loop over both lists in order of their address until we run out of
		// entries in both lists
		while ((pi != null) || (m != null)) {
			// Figure out the address of pi and m. If either is null, then
			// assign
			// them an address equivalent to +infinity
			int pAddr = Integer.MAX_VALUE;
			int mAddr = Integer.MAX_VALUE;
			if (pi != null)
				pAddr = pi.getRegisterValue(CPU.BASE);
			if (m != null)
				mAddr = m.getAddr();

			// If the process has the lowest address then print it and get the
			// next process
			if (mAddr > pAddr) {
				int size = pi.getRegisterValue(CPU.LIM)
						- pi.getRegisterValue(CPU.BASE);
				System.out.print(" Process " + pi.processId + " (addr=" + pAddr
						+ " size=" + size + " words)");
				System.out.print(" @BASE="
						+ pi.getRegisterValue(CPU.BASE) + " @SP="
						+ pi.getRegisterValue(CPU.SP));
				System.out.println();
				if (iterProc.hasNext()) {
					pi = iterProc.next();
				} else {
					pi = null;
				}
			}// if
			else {
				// The free memory block has the lowest address so print it and
				// get the next free memory block
				System.out.println("    Open(addr=" + mAddr + " size="
						+ m.getSize() + ")");
				if (iterFree.hasNext()) {
					m = iterFree.next();
				} else {
					m = null;
				}
			}// else
		}// while

		// Print a footer
		System.out
				.println("-----------------------------------------------------------------");

	}// printMemAlloc

	/*
	 * ======================================================================
	 * Device Management Methods
	 * ----------------------------------------------------------------------
	 */

	/**
	 * registerDevice
	 * 
	 * adds a new device to the list of devices managed by the OS
	 * 
	 * @param dev
	 *            the device driver
	 * @param id
	 *            the id to assign to this device
	 * 
	 */
	public void registerDevice(Device dev, int id) {
		m_devices.add(new DeviceInfo(dev, id));
	}// registerDevice

	/**
	 * findDevice
	 * 
	 * Finds a device's information from within the m_devices vector
	 * 
	 * @param id
	 *            of the device
	 * @return the DeviceInfo object of the device
	 */
	private DeviceInfo findDevice(int id) {
		for (DeviceInfo device : m_devices)
			if (device.getId() == id)
				return device;
		return null;

	}// findDevice

	/*
	 * ======================================================================
	 * Process Management Methods
	 * ----------------------------------------------------------------------
	 */

	/**
	 * selectBlockedProcess
	 * 
	 * select a process to unblock that might be waiting to perform a given
	 * action on a given device. This is a helper method for system calls and
	 * interrupts that deal with devices.
	 * 
	 * @param dev
	 *            the Device that the process must be waiting for
	 * @param op
	 *            the operation that the process wants to perform on the device.
	 *            Use the SYSCALL constants for this value.
	 * @param addr
	 *            the address the process is reading from. If the operation is a
	 *            Write or Open then this value can be anything
	 * 
	 * @return the process to unblock -OR- null if none match the given criteria
	 */
	public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr) {
		ProcessControlBlock selected = null;
		for (ProcessControlBlock pi : m_processes) {
			if (pi.isBlockedForDevice(dev, op, addr)) {
				selected = pi;
				break;
			}
		}// for

		return selected;
	}// selectBlockedProcess

	/**
	 * createIdleProcess
	 * 
	 * creates a one instruction process that immediately exits. This is used to
	 * buy time until device I/O completes and unblocks a legitimate process.
	 * 
	 */
	public void createIdleProcess() {
		int progArr[] = { 0, 0, 0, 0, // SET r0=0
				0, 0, 0, 0, // SET r0=0 (repeated instruction to account for
							// vagaries in student implementation of the CPU
							// class)
				10, 0, 0, 0, // PUSH r0
				15, 0, 0, 0 }; // TRAP

		// Initialize the starting position for this program
		int nextLoadPos = allocBlock(progArr.length - 1);
		if (nextLoadPos == -1) {
			// If space cannot be allocated for it, then return to calling
			// method
			return;
		}

		int baseAddr = nextLoadPos;

		// Load the program into RAM
		for (int i = 0; i < progArr.length; i++) {
			m_RAM.write(baseAddr + i, progArr[i]);
		}

		// Save the register info from the current process (if there is one)
		if (m_currProcess != null) {
			m_currProcess.save(m_CPU);
		}

		// Set the appropriate registers
		m_CPU.setPC(baseAddr);
		m_CPU.setSP(baseAddr + progArr.length + 10);
		m_CPU.setBASE(baseAddr);
		m_CPU.setLIM(baseAddr + progArr.length + 20);

		// Save the relevant info as a new entry in m_processes
		m_currProcess = new ProcessControlBlock(IDLE_PROC_ID);
		m_processes.add(m_currProcess);

	}// createIdleProcess

	/**
	 * printProcessTable **DEBUGGING**
	 * 
	 * prints all the processes in the process table
	 */
	private void printProcessTable() {
		debugPrintln("");
		debugPrintln("Process Table (" + m_processes.size() + " processes)");
		debugPrintln("======================================================================");
		for (ProcessControlBlock pi : m_processes) {
			debugPrintln("    " + pi);
		}// for
		debugPrintln("----------------------------------------------------------------------");

	}// printProcessTable

	/**
	 * removeCurrentProcess
	 * 
	 * removes the process that is currently running from the process table and
	 * selects a new process to run
	 * 
	 */
	public void removeCurrentProcess() {
		freeCurrProcessMemBlock();
		m_processes.remove(m_currProcess);
	}// removeCurrentProcess

	/**
	 * getNextProcess
	 * 
	 * selects the next process to be run based off a variety of criteria
	 * 
	 * @return a reference to a ProcessControlBlock or null if there is no
	 *         non-blocked process
	 */
	ProcessControlBlock getNextProcess() {
		// The process to return
		ProcessControlBlock newProc = null;

		double longestAvgStarve = -1;

		int overallAvgRunTime = 0;

		double overallAvgStarve = 0;

		long avgLastReadyTime = 0;

		// Check if there is a process running
		if (m_currProcess != null) {
			// Get the average run time and the average ready times
			overallAvgRunTime = m_currProcess.overallAvgRunTime();
			avgLastReadyTime = m_currProcess.avgLastReadyTime();
		}

		if (!m_currProcess.isBlocked() && m_processes.contains(m_currProcess)) {
			// Account for load/save penalties
			newProc = m_currProcess;
			longestAvgStarve = newProc.avgStarve + 100;
			overallAvgStarve = newProc.overallAvgStarve();
		}

		for (ProcessControlBlock pi : m_processes) {
			if (!pi.isBlocked()) {
				if ((pi.avgStarve >= longestAvgStarve && pi.avgStarve >= overallAvgStarve)
						|| pi.lastReadyTime >= avgLastReadyTime) {
					longestAvgStarve = pi.avgStarve;
					newProc = pi;
				}

				else if (pi.avgRunTime() >= overallAvgRunTime) {
					if (pi.avgStarve > longestAvgStarve) {
						longestAvgStarve = pi.avgStarve;
						newProc = pi;
					}
				}
			}
		}
		return newProc;
	}// getNextProcess

	/**
	 * getRandomProcess
	 * 
	 * selects a non-Blocked process at random from the ProcessTable.
	 * 
	 * @return a reference to the ProcessControlBlock struct of the selected
	 *         process -OR- null if no non-blocked process exists
	 */
	ProcessControlBlock getRandomProcess() {
		// Calculate a random offset into the m_processes list
		int offset = ((int) (Math.random() * 2147483647)) % m_processes.size();

		// Iterate until a non-blocked process is found
		ProcessControlBlock newProc = null;
		for (int i = 0; i < m_processes.size(); i++) {
			newProc = m_processes.get((i + offset) % m_processes.size());
			if (!newProc.isBlocked()) {
				return newProc;
			}
		}// for

		return null; // no processes are Ready
	}// getRandomProcess

	/**
	 * scheduleNewProcess
	 * 
	 * Selects a new process to run and saves the old process registers before
	 * loading the new ones.
	 * 
	 * If there are no more processes available to run, end simulation.
	 */
	public void scheduleNewProcess() {
		// Check to see if there are no more processes
		if (m_processes.size() == 0) {
			debugPrintln("Schedule new process syscall exit");
			System.exit(ERROR_NO_PROCESSES);
		}

		// Retrieve the next process
		ProcessControlBlock process = getRandomProcess();
		if (process == null) {
			createIdleProcess();
			return;
		}

		// If the current process is not the new process, save all the data, and
		// restore the new one
		if (process != m_currProcess) {
			// save current process's registers
			m_currProcess.save(m_CPU);
			// Set the new process and restore its registers
			m_currProcess = process;
			m_currProcess.restore(m_CPU);
		}

		// If the current process is the idle process, remove it
		if (m_currProcess.processId == 999) {
			SYSCALL_EXIT();
		}
	}// scheduleNewProcess

	/**
	 * createProcess
	 * 
	 * Exports program and loads RAM with program.
	 * 
	 * @param prog
	 *            Program object which contains the program to be loaded
	 * @param allocSize
	 *            Size the RAM that is being allocated
	 */
	public void createProcess(Program prog, int allocSize) {
		// Save the exported program into array
		int[] program = prog.export();

		if (m_currProcess != null)
			m_currProcess.save(m_CPU);

		// find space in RAM for program
		int nextLoadPos = allocBlock(allocSize);
		if (nextLoadPos == -1) {
			return;
		}

		// Set base and limit registers for CPU
		m_CPU.setBASE(nextLoadPos);

		m_CPU.setLIM(allocSize + nextLoadPos);

		m_CPU.setPC(m_CPU.getBASE());

		m_CPU.setSP(m_CPU.getBASE() + prog.getSize() + 1);

		// Load program into memory
		for (int i = 0; i < prog.getSize(); i++)
			m_RAM.write(i + m_CPU.getBASE(), program[i]);

		ProcessControlBlock newProc = new ProcessControlBlock(m_nextProcessID);

		++m_nextProcessID;

		m_processes.add(newProc);

		// Add new process to process vector and make current process
		m_currProcess = newProc;
		m_currProcess.save(m_CPU);
		printMemAlloc();
	}// createProcess

	/**
	 * pushToOtherProc
	 * 
	 * Pushes data to a location on a non-running process Optimization
	 * 
	 * @param addr
	 *            the address to write data to
	 * @param data
	 *            the data to write
	 */
	public void pushToOtherProc(int addr, int data) {
		m_RAM.write(addr, data);
	}

	/*
	 * ======================================================================
	 * Program Management Methods
	 * ----------------------------------------------------------------------
	 */

	/**
	 * addProgram
	 * 
	 * registers a new program with the simulated OS that can be used when the
	 * current process makes an Exec system call. (Normally the program is
	 * specified by the process via a filename but this is a simulation so the
	 * calling process doesn't actually care what program gets loaded.)
	 * 
	 * @param prog
	 *            the program to add
	 * 
	 */
	public void addProgram(Program prog) {
		m_programs.add(prog);
	}// addProgram

	/*
	 * ======================================================================
	 * Interrupt Handlers
	 * ----------------------------------------------------------------------
	 */

	/**
	 * interruptIllegalMemoryAccess
	 * 
	 * If an illegal memory address has been accessed, this method prints an
	 * error and quits the program
	 * 
	 * @param addr
	 *            The address that was illegally accessed
	 */
	public void interruptIllegalMemoryAccess(int addr) {
		System.out.println("FATAL ERROR: Illegal Memory Access Address: "
				+ addr);
		SYSCALL_EXIT();
	}// interruptIllegalMemoryAccess

	/**
	 * interruptDivideByZero
	 * 
	 * If a divide by zero has been attempted this method issues an error
	 * message and quits the program
	 * 
	 */
	public void interruptDivideByZero() {
		System.out.println("FATAL ERROR: Divide By Zero");
		SYSCALL_EXIT();
	}// interruptDivideByZero

	/**
	 * interruptIllegalInstruction
	 * 
	 * If an illegal instruction is attempted this method issues an error
	 * message and quits the program
	 * 
	 * @param instr
	 *            Array of ints which were illegal instructions
	 */
	public void interruptIllegalInstruction(int[] instr) {
		System.out.println("FATAL ERROR: Illegal Instruction");
		m_CPU.printInstr(instr);
		SYSCALL_EXIT();
	}// interruptIllegalInstruction

	/**
	 * interruptIOReadComplete
	 * 
	 * This method handles interrupts due to reads completing
	 * 
	 * @param devID
	 *            The id of the device
	 * @param addr
	 *            The address the interrupt is coming from
	 * @param data
	 *            The data that is being read from the device
	 */
	@Override
	public void interruptIOReadComplete(int devID, int addr, int data) {
		// Find device
		DeviceInfo wantedDevice = findDevice(devID);

		// Make sure the wanted device exists
		if (wantedDevice.device != null) {
			// Find the waiting process and unblock it
			ProcessControlBlock waitingProc = selectBlockedProcess(
					wantedDevice.device, SYSCALL_READ, addr);
			waitingProc.unblock();

			// Find the position of the waitingProc's stack
			int otherSP = waitingProc.getRegisterValue(m_CPU.SP);

			// Write a success code to the correct position in the waiting
			// proc's stack
			waitingProc.setRegisterValue(m_CPU.SP, otherSP + 1);
			otherSP++;
			pushToOtherProc(otherSP, data);
			waitingProc.setRegisterValue(m_CPU.SP, otherSP + 1);
			otherSP++;
			pushToOtherProc(otherSP, SUCCESS_CODE);
		} else {
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
		}
	}// InterrruptIOReadComplete

	/**
	 * interruptIOWriteComplete
	 * 
	 * This method handles interrupts due to writes completing on devices
	 * 
	 * @param devID
	 *            The id of the device
	 * @param addr
	 *            The address that the interrupt came from
	 */
	@Override
	public void interruptIOWriteComplete(int devID, int addr) {
		// Find device
		DeviceInfo wantedDevice = findDevice(devID);

		// PLEEZE BE NOT EMPTY
		if (wantedDevice.device != null) {
			// Find waiting process and unblock
			ProcessControlBlock waitingProc = selectBlockedProcess(
					wantedDevice.device, SYSCALL_WRITE, addr);
			waitingProc.unblock();

			// Find the position of the waitingProc's stack
			int otherSP = waitingProc.getRegisterValue(m_CPU.SP);

			// Write a success code to the correct position in the waiting
			// proc's stack
			waitingProc.setRegisterValue(m_CPU.SP, otherSP + 1);
			otherSP++;
			pushToOtherProc(otherSP, SUCCESS_CODE);
		} else
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
	}// interruptIOWriteComplete

	/**
	 * interruptClock
	 * 
	 * Schedules a new process based on clock times
	 */
	public void interruptClock() {
		scheduleNewProcess();
	}

	/*
	 * ======================================================================
	 * System Calls
	 * ----------------------------------------------------------------------
	 */

	/**
	 * systemCall
	 * 
	 * The system call handler
	 */
	@Override
	public void systemCall() {
		switch (m_CPU.POP()) {
		case SYSCALL_EXIT:
			SYSCALL_EXIT();
			break;
		case SYSCALL_OUTPUT:
			SYSCALL_OUTPUT();
			break;
		case SYSCALL_GETPID:
			SYSCALL_GETPID();
			break;
		case SYSCALL_OPEN:
			syscallOpen();
			break;
		case SYSCALL_CLOSE:
			syscallClose();
			break;
		case SYSCALL_READ:
			syscallRead();
			break;
		case SYSCALL_WRITE:
			syscallWrite();
			break;
		case SYSCALL_EXEC:
			syscallExec();
			break;
		case SYSCALL_YIELD:
			syscallYield();
			break;
		case SYSCALL_COREDUMP:
			SYSCALL_COREDUMP();
			break;
		}
	}// systemCall

	/**
	 * SYSCALL_EXIT
	 * 
	 * Exits the system
	 */
	private void SYSCALL_EXIT() {
		removeCurrentProcess();
 		scheduleNewProcess();
	}

	/**
	 * SYSCALL_OUTPUT
	 * 
	 * Outputs information on the stack to a device
	 */
	private void SYSCALL_OUTPUT() {
		System.out.println("\nOUTPUT: " + m_CPU.POP());
	}

	/**
	 * SYSCALL_GETPID
	 * 
	 * Pushes the process ID of the current process to the stack
	 */
	private void SYSCALL_GETPID() {
		m_CPU.PUSH(m_currProcess.getProcessId());
	}

	/**
	 * syscallOpen
	 * 
	 * Opens a device so that it can be used by the currently running process
	 */
	private void syscallOpen() {
		int deviceID = m_CPU.POP();

		// Check to see that device exists
		DeviceInfo deviceIn = findDevice(deviceID);
		if (deviceIn != null) {
			// Check to see if its already open
			if (!deviceIn.containsProcess(m_currProcess)) {
				// Check to make sure device shareable or unused
				if (deviceIn.unused()
						|| (deviceIn.procs.size() > 0 && deviceIn.device
								.isSharable())) {
					deviceIn.procs.add(m_currProcess);
					m_CPU.PUSH(SUCCESS_CODE);
				} else {
					deviceIn.addProcess(m_currProcess);
					m_currProcess.block(m_CPU, deviceIn.getDevice(),
							SYSCALL_OPEN, 0);
					m_CPU.PUSH(SUCCESS_CODE);
					scheduleNewProcess();
				}
			} else
				m_CPU.PUSH(ERROR_DEVICE_OPEN);
		} else
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
	}// syscallOpen

	/**
	 * syscallClose
	 * 
	 * Closes a device so that it can no longer be used by the current process
	 */
	private void syscallClose() {
		int deviceID = m_CPU.POP();

		// Check for device existence
		DeviceInfo deviceIn = findDevice(deviceID);
		if (deviceIn != null) {
			// Check if device open
			if (deviceIn.containsProcess(m_currProcess)) {
				deviceIn.removeProcess(m_currProcess);

				// Find out if there is another process waiting for this device
				// and unblock it if yes
				ProcessControlBlock waitingProc = selectBlockedProcess(
						deviceIn.getDevice(), SYSCALL_OPEN, 0);
				if (waitingProc != null)
					waitingProc.unblock();

				m_CPU.PUSH(SUCCESS_CODE);
			} else
				m_CPU.PUSH(ERROR_DEVICE_NOT_OPEN);
		} else
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
	}// syscallClose

	/**
	 * syscallRead
	 * 
	 * Reads information from a device by popping data off of the stack
	 */
	private void syscallRead() {
		// get necessary information from the stack
		int address = m_CPU.POP();
		int deviceID = m_CPU.POP();

		// Check for device existence
		DeviceInfo deviceIn = findDevice(deviceID);
		if (deviceIn != null) {
			// Check to see if device is open
			if (deviceIn.containsProcess(m_currProcess)) {
				// Check if device readable
				if (deviceIn.device.isReadable()) {
					if (deviceIn.device.isAvailable()) {
						deviceIn.device.read(address);

						// Now block and wait for it to come back
						m_currProcess.block(m_CPU, deviceIn.getDevice(),
								SYSCALL_READ, address);
					} else {
						m_CPU.PUSH(deviceID);
						m_CPU.PUSH(address);
						m_CPU.PUSH(SYSCALL_READ);
						m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
					}
					scheduleNewProcess();
				} else
					m_CPU.PUSH(ERROR_DEVICE_NOT_READABLE);
			} else
				m_CPU.PUSH(ERROR_DEVICE_NOT_OPEN);
		} else
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
	}// syscallRead

	/**
	 * syscallWrite
	 * 
	 * Writes data to a device by pushing it onto the stack
	 */
	private void syscallWrite() {
		// Get necessary information from the stack
		int data = m_CPU.POP();
		int address = m_CPU.POP();
		int deviceID = m_CPU.POP();

		// Check for device existence
		DeviceInfo deviceIn = findDevice(deviceID);
		if (deviceIn != null) {
			// Check if device open
			if (deviceIn.containsProcess(m_currProcess)) {
				// Check if device writeable
				if (deviceIn.device.isWriteable()) {
					if (deviceIn.device.isAvailable()) {
						// Write data to device
						deviceIn.device.write(address, data);

						// Block the process and wait for write to finish
						m_currProcess.block(m_CPU, deviceIn.device,
								SYSCALL_WRITE, address);
					} else {
						// Set PC to rexecute trap instruction and push all data
						// back to stack
						m_CPU.PUSH(deviceID);
						m_CPU.PUSH(address);
						m_CPU.PUSH(data);
						m_CPU.PUSH(SYSCALL_WRITE);
						m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
					}
					scheduleNewProcess();
				} else
					m_CPU.PUSH(ERROR_DEVICE_NOT_WRITEABLE);
			} else
				m_CPU.PUSH(ERROR_DEVICE_NOT_OPEN);
		} else
			m_CPU.PUSH(ERROR_DEVICE_EXISTENCE);
	}// syscallWrite

	/**
	 * syscallExec
	 * 
	 * creates a new process. The program used to create that process is chosen
	 * semi-randomly from all the programs that have been registered with the OS
	 * via {@link #addProgram}. Limits are put into place to ensure that each
	 * process is run an equal number of times. If no programs have been
	 * registered then the simulation is aborted with a fatal error.
	 * 
	 */
	private void syscallExec() {
		// If there is nothing to run, abort. This should never happen.
		if (m_programs.size() == 0) {
			System.err.println("ERROR!  syscallExec has no programs to run.");
			System.exit(-1);
		}

		// find out which program has been called the least and record how many
		// times it has been called
		int leastCallCount = m_programs.get(0).callCount;
		for (Program prog : m_programs) {
			if (prog.callCount < leastCallCount) {
				leastCallCount = prog.callCount;
			}
		}

		// Create a vector of all programs that have been called the least
		// number
		// of times
		Vector<Program> cands = new Vector<Program>();
		for (Program prog : m_programs) {
			cands.add(prog);
		}

		// Select a random program from the candidates list
		Random rand = new Random();
		int pn = rand.nextInt(m_programs.size());
		Program prog = cands.get(pn);

		// Determine the address space size using the default if available.
		// Otherwise, use a multiple of the program size.
		int allocSize = prog.getDefaultAllocSize();
		if (allocSize <= 0) {
			allocSize = prog.getSize() * 2;
		}

		int procVectorSize = m_processes.size();
		// Load the program into RAM
		createProcess(prog, allocSize);

		if (procVectorSize != m_processes.size()) {
			// Adjust the PC since it's about to be incremented by the CPU
			m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
		}
	}// syscallExec

	/**
	 * Process can move from Running to Ready state
	 */
	private void syscallYield() {
		scheduleNewProcess();
	}// syscallYield

	/**
	 * Process failure leads to this
	 */
	private void SYSCALL_COREDUMP() {
		m_CPU.regDump();
		System.out.println("OUTPUT: " + m_CPU.POP());
		System.out.println("OUTPUT: " + m_CPU.POP());
		System.out.println("OUTPUT: " + m_CPU.POP());
		SYSCALL_EXIT();
	}// SYSCALL_COREDUMP

	/**
	 * class ProcessControlBlock
	 * 
	 * This class contains information about a currently active process.
	 */
	private class ProcessControlBlock implements
			Comparable<ProcessControlBlock> {

		/**
		 * These are the process' current registers. If the process is in the
		 * "running" state then these are out of date
		 */
		private int[] registers = null;

		/**
		 * If this process is blocked a reference to the Device is stored here
		 */
		private Device blockedForDevice = null;

		/**
		 * If this process is blocked a reference to the type of I/O operation
		 * is stored here (use the SYSCALL constants defined in SOS)
		 */
		private int blockedForOperation = -1;

		/**
		 * If this process is blocked reading from a device, the requested
		 * address is stored here.
		 */
		private int blockedForAddr = -1;

		/**
		 * a unique id for this process
		 */
		private int processId = 0;

		/**
		 * the time it takes to load and save registers, specified as a number
		 * of CPU ticks
		 */
		private static final int SAVE_LOAD_TIME = 30;

		/**
		 * Used to store the system time when a process is moved to the Ready
		 * state.
		 */
		private int lastReadyTime = -1;

		/**
		 * Used to store the number of times this process has been in the ready
		 * state
		 */
		private int numReady = 0;

		/**
		 * Used to store the maximum starve time experienced by this process
		 */
		private int maxStarve = -1;

		/**
		 * Used to store the average starve time for this process
		 */
		private double avgStarve = 0;

		/**
		 * Used to store the total run time of this process
		 */
		private int totalRunTime = 0;

		/**
		 * Stores the last time this process was started
		 */
		private int lastStartTime = 0;

		/**
		 * Stores the last time this process was saved
		 */
		private int lastEndTime = 0;

		/**
		 * Stores the average running time of this process
		 */
		private int avgRunTime = 0;

		/**
		 * constructor
		 * 
		 * @param pid
		 *            a process id for the process. The caller is responsible
		 *            for making sure it is unique.
		 */
		public ProcessControlBlock(int pid) {
			this.processId = pid;
		}

		/**
		 * @return the current process' id
		 */
		public int getProcessId() {
			return this.processId;
		}

		/**
		 * @return the last time this process was put in the Ready state
		 */
		public long getLastReadyTime() {
			return lastReadyTime;
		}

		public long avgLastReadyTime() {
			int count = 0;
			long readyTime = 0;
			for (ProcessControlBlock pi : m_processes) {
				readyTime += pi.lastReadyTime;
				count++;
			}
			if (count != 0) {
				return readyTime / count;
			} else
				return 0;
		}

		/**
		 * avgRunTime
		 * 
		 * @return the average running time of a process
		 */
		public int avgRunTime() {
			if (numReady != 0) {
				avgRunTime = totalRunTime / numReady;
				return avgRunTime;
			}
			return 0;
		}

		/**
		 * overallAvgRunTime
		 * 
		 * @return the overall average run time of all processes
		 */
		public int overallAvgRunTime() {
			int runTime = 0;
			int count = 0;
			for (ProcessControlBlock pi : m_processes) {
				runTime += pi.avgRunTime;
				count++;
			}

			if (count != 0)
				return runTime / count;
			return 0;
		}

		/**
		 * getLastRunTime
		 * 
		 * @return the last run time of the process
		 */
		public int getLastRunTime() {
			return lastEndTime - lastStartTime;
		}

		/**
		 * move
		 * 
		 * Moves a process to a new alloc block in RAM given the new base for
		 * that block
		 * 
		 * @param newBase
		 *            the address of the targeted alloc block
		 * @return true if successful, false if not
		 */
		public void move(int newBase) {
			
			int curBase, curLim;
		
			curBase = registers[m_CPU.BASE];
			
			curLim = registers[m_CPU.LIM];
			
			setRegisterValue(m_CPU.BASE, newBase);
			
			int size = curLim - curBase;
			
			setRegisterValue(m_CPU.LIM, size + newBase);
			
			//Iterator for writing process to new memory location
			int j = newBase;

			// Write the process to a new location
			for (int i = curBase; i <= curLim; ++i) {
				m_RAM.write(j, m_RAM.read(i));
				++j;
			}//inner for

			//Reset all the process values
			int offsetSP = registers[m_CPU.SP] - curBase;
			int offsetPC = registers[m_CPU.PC] - curBase;
			setRegisterValue(m_CPU.SP, newBase + offsetSP);
			setRegisterValue(m_CPU.PC, newBase + offsetPC);
			
			//If the current process is the one that is being moved, reset CPU registers
			if(this == m_currProcess)
			{
				m_CPU.setSP(newBase + offsetSP);
				m_CPU.setPC(newBase + offsetPC);
				m_CPU.setBASE(newBase);
				m_CPU.setLIM(newBase + size);
			}

			debugPrintln("Process " + processId + " was moved from "
					+ curBase + " to " + newBase);
		}// move

		/**
		 * save
		 * 
		 * saves the current CPU registers into this.registers
		 * 
		 * @param cpu
		 *            the CPU object to save the values from
		 */
		public void save(CPU cpu) {
			lastEndTime = m_CPU.getTicks();
			// A context switch is expensive. We simulate that here by
			// adding ticks to m_CPU
			m_CPU.addTicks(SAVE_LOAD_TIME);

			// Save the registers
			int[] regs = cpu.getRegisters();
			this.registers = new int[CPU.NUMREG];
			for (int i = 0; i < CPU.NUMREG; i++) {
				this.registers[i] = regs[i];
			}

			// Assuming this method is being called because the process is
			// moving
			// out of the Running state, record the current system time for
			// calculating starve times for this process. If this method is
			// being called for a Block, we'll adjust lastReadyTime in the
			// unblock method.
			numReady++;
			lastReadyTime = m_CPU.getTicks();
			totalRunTime += getLastRunTime();

		}// save

		/**
		 * restore
		 * 
		 * restores the saved values in this.registers to the current CPU's
		 * registers
		 * 
		 * @param cpu
		 *            the CPU object to restore the values to
		 */
		public void restore(CPU cpu) {
			// A context switch is expensive. We simluate that here by
			// adding ticks to m_CPU
			m_CPU.addTicks(SAVE_LOAD_TIME);

			// Restore the register values
			int[] regs = cpu.getRegisters();
			for (int i = 0; i < CPU.NUMREG; i++) {
				regs[i] = this.registers[i];
			}

			// Record the starve time statistics
			int starveTime = m_CPU.getTicks() - lastReadyTime;
			if (starveTime > maxStarve) {
				maxStarve = starveTime;
			}
			double d_numReady = (double) numReady;
			avgStarve = avgStarve * (d_numReady - 1.0) / d_numReady;
			avgStarve = avgStarve + (starveTime * (1.0 / d_numReady));

			lastStartTime = m_CPU.getTicks();
		}// restore

		/**
		 * block
		 * 
		 * blocks the current process to wait for I/O. The caller is responsible
		 * for calling {@link CPU#scheduleNewProcess} after calling this method.
		 * 
		 * @param cpu
		 *            the CPU that the process is running on
		 * @param dev
		 *            the Device that the process must wait for
		 * @param op
		 *            the operation that the process is performing on the
		 *            device. Use the SYSCALL constants for this value.
		 * @param addr
		 *            the address the process is reading from (for SYSCALL_READ)
		 * 
		 */
		public void block(CPU cpu, Device dev, int op, int addr) {
			blockedForDevice = dev;
			blockedForOperation = op;
			blockedForAddr = addr;

		}// block

		/**
		 * unblock
		 * 
		 * moves this process from the Blocked (waiting) state to the Ready
		 * state.
		 * 
		 */
		public void unblock() {
			// Reset the info about the block
			blockedForDevice = null;
			blockedForOperation = -1;
			blockedForAddr = -1;

			// Assuming this method is being called because the process is
			// moving
			// from the Blocked state to the Ready state, record the current
			// system time for calculating starve times for this process.
			lastReadyTime = m_CPU.getTicks();

		}// unblock

		/**
		 * isBlocked
		 * 
		 * @return true if the process is blocked
		 */
		public boolean isBlocked() {
			return (blockedForDevice != null);
		}// isBlocked

		/**
		 * isBlockedForDevice
		 * 
		 * Checks to see if the process is blocked for the given device,
		 * operation and address. If the operation is not an open, the given
		 * address is ignored.
		 * 
		 * @param dev
		 *            check to see if the process is waiting for this device
		 * @param op
		 *            check to see if the process is waiting for this operation
		 * @param addr
		 *            check to see if the process is reading from this address
		 * 
		 * @return true if the process is blocked by the given parameters
		 */
		public boolean isBlockedForDevice(Device dev, int op, int addr) {
			if ((blockedForDevice == dev) && (blockedForOperation == op)) {
				if (op == SYSCALL_OPEN) {
					return true;
				}

				if (addr == blockedForAddr) {
					return true;
				}
			}// if

			return false;
		}// isBlockedForDevice

		/**
		 * overallAvgStarve
		 * 
		 * @return the overall average starve time for all currently running
		 *         processes
		 * 
		 */
		public double overallAvgStarve() {
			double result = 0.0;
			int count = 0;
			for (ProcessControlBlock pi : m_processes) {
				if (pi.avgStarve > 0) {
					result = result + pi.avgStarve;
					count++;
				}
			}
			if (count > 0) {
				result = result / count;
			}

			return result;
		}// overallAvgStarve

		/**
		 * compareTo
		 * 
		 * compares this to another ProcessControlBlock object based on the BASE
		 * addr register. Read about Java's Collections class for info on how
		 * this method can be quite useful to you.
		 */
		public int compareTo(ProcessControlBlock pi) {
			return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
		}

		/**
		 * getRegisterValue
		 * 
		 * Retrieves the value of a process' register that is stored in this
		 * object (this.registers).
		 * 
		 * @param idx
		 *            the index of the register to retrieve. Use the constants
		 *            in the CPU class
		 * @return one of the register values stored in in this object or -999
		 *         if an invalid index is given
		 */
		public int getRegisterValue(int idx) {
			if ((idx < 0) || (idx >= CPU.NUMREG)) {
				return -999; // invalid index
			}

			return this.registers[idx];
		}// getRegisterValue

		/**
		 * setRegisterValue
		 * 
		 * Sets the value of a process' register that is stored in this object
		 * (this.registers).
		 * 
		 * @param idx
		 *            the index of the register to set. Use the constants in the
		 *            CPU class. If an invalid index is given, this method does
		 *            nothing.
		 * @param val
		 *            the value to set the register to
		 */
		public void setRegisterValue(int idx, int val) {
			if ((idx < 0) || (idx >= CPU.NUMREG)) {
				return; // invalid index
			}

			this.registers[idx] = val;
		}// setRegisterValue

		/**
		 * toString **DEBUGGING**
		 * 
		 * @return a string representation of this class
		 */
		public String toString() {
			// Print the Process ID and process state (READY, RUNNING, BLOCKED)
			String result = "Process id " + processId + " ";
			if (isBlocked()) {
				result = result + "is BLOCKED for ";
				// Print device, syscall and address that caused the BLOCKED
				// state
				if (blockedForOperation == SYSCALL_OPEN) {
					result = result + "OPEN";
				} else {
					result = result + "WRITE @" + blockedForAddr;
				}
				for (DeviceInfo di : m_devices) {
					if (di.getDevice() == blockedForDevice) {
						result = result + " on device #" + di.getId();
						break;
					}
				}
				result = result + ": ";
			} else if (this == m_currProcess) {
				result = result + "is RUNNING: ";
			} else {
				result = result + "is READY: ";
			}

			// Print the register values stored in this object. These don't
			// necessarily match what's on the CPU for a Running process.
			if (registers == null) {
				result = result + "<never saved>";
				return result;
			}

			for (int i = 0; i < CPU.NUMGENREG; i++) {
				result = result + ("r" + i + "=" + registers[i] + " ");
			}// for
			result = result + ("PC=" + registers[CPU.PC] + " ");
			result = result + ("SP=" + registers[CPU.SP] + " ");
			result = result + ("BASE=" + registers[CPU.BASE] + " ");
			result = result + ("LIM=" + registers[CPU.LIM] + " ");

			// Print the starve time statistics for this process
			result = result + "\n\t\t\t";
			result = result + " Max Starve Time: " + maxStarve;
			result = result + " Avg Starve Time: " + avgStarve;

			return result;
		}// toString

	}// class ProcessControlBlock

	/**
	 * class DeviceInfo
	 * 
	 * This class contains information about a device that is currently
	 * registered with the system.
	 */
	private class DeviceInfo {
		/** every device has a unique id */
		private int id;
		/** a reference to the device driver for this device */
		private Device device;
		/** a list of processes that have opened this device */
		private Vector<ProcessControlBlock> procs;

		/**
		 * constructor
		 * 
		 * @param d
		 *            a reference to the device driver for this device
		 * @param initID
		 *            the id for this device. The caller is responsible for
		 *            guaranteeing that this is a unique id.
		 */
		public DeviceInfo(Device d, int initID) {
			this.id = initID;
			this.device = d;
			d.setId(initID);
			this.procs = new Vector<ProcessControlBlock>();
		}

		/** @return the device's id */
		public int getId() {
			return this.id;
		}

		/** @return this device's driver */
		public Device getDevice() {
			return this.device;
		}

		/** Register a new process as having opened this device */
		public void addProcess(ProcessControlBlock pi) {
			procs.add(pi);
		}

		/** Register a process as having closed this device */
		public void removeProcess(ProcessControlBlock pi) {
			procs.remove(pi);
		}

		/** Does the given process currently have this device opened? */
		public boolean containsProcess(ProcessControlBlock pi) {
			return procs.contains(pi);
		}

		/** Is this device currently not opened by any process? */
		public boolean unused() {
			return procs.size() == 0;
		}

	}// class DeviceInfo

	/**
	 * class MemBlock
	 * 
	 * This class contains relevant info about a memory block in RAM.
	 * 
	 */
	private class MemBlock implements Comparable<MemBlock> {
		/** the address of the block */
		private int m_addr;
		/** the size of the block */
		private int m_size;

		/**
		 * ctor does nothing special
		 */
		public MemBlock(int addr, int size) {
			m_addr = addr;
			m_size = size;
		}

		/** accessor methods */
		public int getAddr() {
			return m_addr;
		}

		public int getSize() {
			return m_size;
		}

		/**
		 * compareTo
		 * 
		 * compares this to another MemBlock object based on address
		 */
		public int compareTo(MemBlock m) {
			return this.m_addr - m.m_addr;
		}

	}// class MemBlock

};// class SOS

