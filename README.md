DMRServer is a simplified backend service for hosting private or personal DMR Talkgroups.  

Usage:

java -jar DMRServer-0.1-executable.jar -port 62031



Authentication 

You must configure auth.properties in order to start the server.



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
