## Point slope form calculator

SET R0 4		#SET x1 value
SET R1 9		#SET y1 value
SET R2 3		#SET m value

MUL R0 R0 R2	#Multiply m and x1 value
SET R3 -1		#Set R3 to -1 to make m*x1 negative
MUL R0 R0 R3	#This is now -m*x1
ADD R0 R0 R1	#Add -m*x1 to y1 (b value)!
