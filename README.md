DMRServer is a simplified backend service for hosting private or personal DMR Talkgroups.  

-----------------------------------------------------------------------------------------------

Features:

-Host your own talkgroups on a private server

-No talkgroup configuration, we pass everything through

-Individual or sharded accesss passwords

-----------------------------------------------------------------------------------------------

Prerequisites:

You must have java installed to use this product.  You can type: java -version  at the command line to see if you have this.

-----------------------------------------------------------------------------------------------


Usage:

java -jar DMRServer-1.0-executable.jar -port 62031

-----------------------------------------------------------------------------------------------

Authentication 

You must configure auth.properties in order to start the server.

-----------------------------------------------------------------------------------------------

Example 

[auth.properties]

#create a wildcard password for all users

*=xxxxx12345

#create a password for a spoecific DMR Id

1234567=xxxxx12345

#create a password for a DMR Id + any ESSID

1234567*=xxxxx12345

#create a password for a DMR Id + ESSID "01"

123456701=xxxxx12345
