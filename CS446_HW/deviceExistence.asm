####################################################
#This program tries to open a device that does not exist
###################################################

#Opening device
SET r0 5       #device #5 (nonexistent console output)
PUSH r0        #push device number onto stack
SET r4 3       #OPEN sys call id
PUSH r4        #push sys call id
TRAP           #open the device

#Failure check
POP r4         #get error code from the system call
SET r0 0       #Succes code
BNE r0 r4 exit #exit program on error

:exit
SET r4 0		#Exit syscall ID
PUSH r4			#Push syscall ID to stack
TRAP			#Actually exit


