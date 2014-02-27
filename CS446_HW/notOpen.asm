####################################################
#This program tests ERROR_DEVICE_NOT_OPEN by calling
#WRITE system call without opening a device.
###################################################

#Call WRITE from device
SET r2 1       #device id is equal to 1
PUSH r2        #push device id
PUSH r0        #push address 
PUSH r1        #push value to send to device
SET r2 6       #call WRITE system call
PUSH r2        #push the sys call id
TRAP           #print the value returned by device

#Failure check
POP r2         #get error code
SET r0 0       #Success code
BNE r0 r2 exit #exit program

:exit
SET r4 0		#Exit syscall ID
PUSH r4			#Push syscall ID to stack
TRAP			#Actually exit
