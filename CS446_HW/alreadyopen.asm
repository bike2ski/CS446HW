####################################################
#Test program checks ERROR_DEVICE_OPEN by attempting
#to open a device twice.
###################################################

#Opening device
:loop
SET r0 1       #device id is 1
PUSH r0        #push device number onto stack
SET r4 3       #call OPEN sys call
PUSH r4        #push sys call id
TRAP           #open the device

#Failure check
POP r4         #get error code from the system call
SET r0 0       #Succes code
BNE r0 r4 exit #exit program on error
BRANCH loop

:exit
SET r4 0		#Exit syscall ID
PUSH r4			#Push syscall ID to stack
TRAP			#Actually exit





