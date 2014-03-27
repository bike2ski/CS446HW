package sos;


/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer. This includes a processor chip, RAM and I/O devices. It is
 * designed to demonstrate a simulated operating system (SOS).
 * 
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU implements Runnable{

    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    // These constants define the instructions available on the chip
    public static final int SET = 0; /* set value of reg */
    public static final int ADD = 1; // put reg1 + reg2 into reg3
    public static final int SUB = 2; // put reg1 - reg2 into reg3
    public static final int MUL = 3; // put reg1 * reg2 into reg3
    public static final int DIV = 4; // put reg1 / reg2 into reg3
    public static final int COPY = 5; // copy reg1 to reg2
    public static final int BRANCH = 6; // goto address in reg
    public static final int BNE = 7; // branch if not equal
    public static final int BLT = 8; // branch if less than
    public static final int POP = 9; // load value from stack
    public static final int PUSH = 10; // save value to stack
    public static final int LOAD = 11; // load value from heap
    public static final int SAVE = 12; // save value to heap
    public static final int TRAP = 15; // system call

    // These constants define the indexes to each register
    public static final int R0 = 0; // general purpose registers
    public static final int R1 = 1;
    public static final int R2 = 2;
    public static final int R3 = 3;
    public static final int R4 = 4;
    public static final int PC = 5; // program counter
    public static final int SP = 6; // stack pointer
    public static final int BASE = 7; // bottom of currently accessible RAM
    public static final int LIM = 8; // top of accessible RAM
    public static final int NUMREG = 9; // number of registers

    // Misc constants
    public static final int NUMGENREG = PC; // the number of general registers
    public static final int INSTRSIZE = 4; // number of ints in a single instr +
                                           // args. (Set to a fixed value for
                                           // simplicity.)

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------
    /**
     * specifies whether the CPU should output details of its work
     **/
    private boolean m_verbose = false;

    /**
     * This array contains all the registers on the "chip".
     **/
    private int m_registers[];

    /**
     * A pointer to the RAM used by this CPU
     * 
     * @see RAM
     **/
    private RAM m_RAM = null;
    
    /**
     * This is a reference to the CPU's interrupt controller
     */
    private InterruptController m_IC;
    
    /**
     * How often there is a clock interrupt
     */
    private int CLOCK_FREQ = 5;
    
    /**
     * How many CPU cycles have occurred in the simulation
     */
    private int m_ticks;

    // ======================================================================
    // Methods
    // ----------------------------------------------------------------------

    /**
     * CPU ctor
     * 
     * Intializes all member variables.
     */
    public CPU(RAM ram, InterruptController control) {
        m_registers = new int[NUMREG];
        for (int i = 0; i < NUMREG; i++) {
            m_registers[i] = 0;
        }
        m_RAM = ram;
        m_IC = control;
    }// CPU ctor

    /**
     * getPC
     * 
     * @return the value of the program counter
     */
    public int getPC() {
        return m_registers[PC];
    }

    /**
     * getSP
     * 
     * @return the value of the stack pointer
     */
    public int getSP() {
        return m_registers[SP];
    }

    /**
     * getBASE
     * 
     * @return the value of the base register
     */
    public int getBASE() {
        return m_registers[BASE];
    }

    /**
     * getLIMIT
     * 
     * @return the value of the limit register
     */
    public int getLIM() {
        return m_registers[LIM];
    }

    /**
     * getRegisters
     * 
     * @return the registers
     */
    public int[] getRegisters() {
        return m_registers;
    }
    
    /**
     * getTicks
     * 
     * @return number of CPU cycles elapsed in simulation
     */
    public int getTicks()
    {
    	return m_ticks;
    }
    
    /**
     * addTicks
     * 
     * adds a specified number to m_ticks
     * 
     * @param numTicks - the number of CPU cycles to add
     */
    public void addTicks(int numTicks)
    {
    	m_ticks += numTicks;
    }

    /**
     * setPC
     * 
     * @param v
     *            the new value of the program counter
     */
    public void setPC(int v) {
        m_registers[PC] = v;
    }

    /**
     * setSP
     * 
     * @param v
     *            the new value of the stack pointer
     */
    public void setSP(int v) {
        m_registers[SP] = v;
    }

    /**
     * setBASE
     * 
     * @param v
     *            the new value of the base register
     */
    public void setBASE(int v) {
        m_registers[BASE] = v;
    }

    /**
     * setLIM
     * 
     * @param v
     *            the new value of the limit register
     */
    public void setLIM(int v) {
        m_registers[LIM] = v;
    }
    
    /**
     * a reference to the trap handler for this CPU.  On a real CPU this would
     * simply be an address that the PC register is set to.
     */
    private TrapHandler m_TH = null;

    /**
     * registerTrapHandler
     *
     * allows SOS to register itself as the trap handler 
     */
    public void registerTrapHandler(TrapHandler th)
    {
        m_TH = th;
    }
    
    private void checkForClockInterrupt()
    {
    	if((m_ticks % CLOCK_FREQ) == 0)
    	{
    		m_TH.interruptClock();
    	}
    }
    
    /**
     * checkForIOInterrupt
     *
     * Checks the databus for signals from the interrupt controller and, if
     * found, invokes the appropriate handler in the operating system.
     *
     */
    private void checkForIOInterrupt()
    {
        //If there is no interrupt to process, do nothing
        if (m_IC.isEmpty())
        {
            return;
        }
        
        //Retreive the interrupt data
        int[] intData = m_IC.getData();

        //Report the data if in verbose mode
        if (m_verbose)
        {
            System.out.println("CPU received interrupt: type=" + intData[0]
                               + " dev=" + intData[1] + " addr=" + intData[2]
                               + " data=" + intData[3]);
        }

        //Dispatch the interrupt to the OS
        switch(intData[0])
        {
            case InterruptController.INT_READ_DONE:
                m_TH.interruptIOReadComplete(intData[1], intData[2], intData[3]);
                break;
            case InterruptController.INT_WRITE_DONE:
                m_TH.interruptIOWriteComplete(intData[1], intData[2]);
                break;
            default:
                System.out.println("CPU ERROR:  Illegal Interrupt Received.");
                System.exit(-1);
                break;
        }//switch

    }//checkForIOInterrupt

    
    //======================================================================
    //Callback Interface
    //----------------------------------------------------------------------
    /**
     * TrapHandler
     *
     * This interface should be implemented by the operating system to allow the
     * simulated CPU to generate hardware interrupts and system calls.
     */
    public interface TrapHandler
    {
        void interruptIllegalMemoryAccess(int addr);
        void interruptDivideByZero();
        void interruptIllegalInstruction(int[] instr);
        void systemCall();
        public void interruptIOReadComplete(int devID, int addr, int data);
        public void interruptIOWriteComplete(int devID, int addr);
        void interruptClock();
    };//interface TrapHandler

    /**
     * regDump
     * 
     * Prints the values of the registers. Useful for debugging.
     */
    public void regDump() {
        for (int i = 0; i < NUMGENREG; i++) {
            System.out.print("r" + i + "=" + m_registers[i] + " ");
        }// for
        System.out.print("PC=" + m_registers[PC] + " ");
        System.out.print("SP=" + m_registers[SP] + " ");
        System.out.print("BASE=" + m_registers[BASE] + " ");
        System.out.print("LIM=" + m_registers[LIM] + " ");
        System.out.println("");
    }// regDump

    /**
     * printIntr
     * 
     * Prints a given instruction in a user readable format. Useful for
     * debugging.
     * 
     * @param instr
     *            the current instruction
     */
    public void printInstr(int[] instr) {
        switch (instr[0]) {
        case SET:
            System.out.println("SET R" + instr[1] + " = " + instr[2]);
            break;
        case ADD:
            System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R" + instr[3]);
            break;
        case SUB:
            System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R" + instr[3]);
            break;
        case MUL:
            System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R" + instr[3]);
            break;
        case DIV:
            System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R" + instr[3]);
            break;
        case COPY:
            System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
            break;
        case BRANCH:
            System.out.println("BRANCH @" + instr[1]);
            break;
        case BNE:
            System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @" + instr[3]);
            break;
        case BLT:
            System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @" + instr[3]);
            break;
        case POP:
            System.out.println("POP R" + instr[1]);
            break;
        case PUSH:
            System.out.println("PUSH R" + instr[1]);
            break;
        case LOAD:
            System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
            break;
        case SAVE:
            System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
            break;
        case TRAP:
            System.out.print("TRAP ");
            break;
        default: // should never be reached
            System.out.println("?? ");
            break;
        }// switch

    }// printInstr

    /**
     * isBadAddress
     * 
     * Checks address to ensure address is within the valid range.
     * 
     * @param address
     *            the address to check validity of
     * @return false if the address valid  and true otherwise
     */
    public void isBadAddres(int address) {
        if (!((address >= getBASE()) && (address < getLIM() + getBASE())))
        	m_TH.interruptIllegalMemoryAccess(address);
    }

    /**
     * POP
     * 
     * Pops item off stack and saves it into appropriate register
     * 
     * @param address
     *            the address to save the popped item to
     */
    public int POP() {
        // Read from RAM at location of the stack pointer and save result into
        // specified register.
    	int val =  m_RAM.read(m_registers[SP]);
        m_registers[SP]--;
        return val;
    }

    /**
     * PUSH
     * 
     * Pushes a value to stack.
     * 
     * @param val
     *            the value to be pushed to the stack
     */
    public void PUSH(int val) {
        (m_registers[SP]) = m_registers[SP] +1 ;
        // Write to RAM at location of stack pointer the value of parameter.
        m_RAM.write(m_registers[SP], val);
    }

    /**
     * run
     * 
     * Creates a virtual CPU. Loads and executes instructions contained in
     * RAM.
     */
    public void run() {
        // Loop to simulate execution of instructions loaded into RAM
        while (true) {
        	
        	//Check for an IO interrupt
        	checkForIOInterrupt();
            // Get instructions
            int nextInstr[] = m_RAM.fetch(getPC());

            // If in verbose mode, print the instructions
            if (m_verbose == true) {
                regDump();
                printInstr(nextInstr);
            }

            // Simulate CPU. nextInstr[0] contains the opcode to determine what instruction to perform.
            switch (nextInstr[0]) {
            case SET:
                m_registers[nextInstr[1]] = nextInstr[2];
                break;

            case ADD:
                m_registers[nextInstr[1]] = m_registers[nextInstr[2]] + m_registers[nextInstr[3]];
                break;

            case SUB:
                m_registers[nextInstr[1]] = m_registers[nextInstr[2]] - m_registers[nextInstr[3]];
                break;

            case MUL:
                m_registers[nextInstr[1]] = m_registers[nextInstr[2]] * m_registers[nextInstr[3]];
                break;

            case DIV:
            	if(m_registers[nextInstr[3]] == 0){
            		m_TH.interruptDivideByZero();
            	}
            	else{
            		m_registers[nextInstr[1]] = m_registers[nextInstr[2]] / m_registers[nextInstr[3]];
            	}
                break;

            case COPY:
                m_registers[nextInstr[1]] = m_registers[nextInstr[2]];
                break;

            case BRANCH:
               isBadAddres(nextInstr[1] + getBASE());
                // PC is set to the address passed in arg1.
                setPC(nextInstr[1] + getBASE() - 4);
                break;

            case BNE:
                isBadAddres(nextInstr[3] + getBASE());
                // When arg1 and arg2 are not equal, set PC to arg3
                if (m_registers[nextInstr[1]] != m_registers[nextInstr[2]]) {
                    setPC(nextInstr[3] + getBASE() - 4);
                }
                break;

            case BLT:
                isBadAddres(nextInstr[3] + getBASE());
                // When arg1 is less than arg2, set PC to arg3
                if (m_registers[nextInstr[1]] < m_registers[nextInstr[2]]) {
                    setPC(nextInstr[3] + getBASE() - 4);
                }
                break;

            case POP:
                // Call pop with register item is to be stored in
            	m_registers[nextInstr[1]] =  POP();
                break;

            case PUSH:
                // Push onto the stack the value stored in register specified by
                // arg1
                PUSH(m_registers[nextInstr[1]]);
                break;

            case LOAD:
            	isBadAddres(m_registers[nextInstr[2]] + getBASE());
               // Store into register specified by arg1 the value stored in
               // RAM at location specified by arg2
                m_registers[nextInstr[1]] = m_RAM.read((m_registers[nextInstr[2]] + getBASE()));
                break;

            case SAVE:
                isBadAddres(m_registers[nextInstr[2]] + getBASE());
                // Save into location specified by arg2 the value specified
                // by arg1
               m_RAM.write((m_registers[nextInstr[2]] + getBASE()), m_registers[nextInstr[1]]);
                break;

            case TRAP:
                m_TH.systemCall();
                break;

                // Should never be hit
            default:
                System.out.println("FATAL ERROR: Invalid Opcode ");
                break;

            }//SWITCH
            
            ++m_ticks;
            checkForClockInterrupt();
            
            // Update PC after switch
            setPC(m_registers[PC] + INSTRSIZE);
            
        }//while(TRUE)
    }// run

};// class CPU
