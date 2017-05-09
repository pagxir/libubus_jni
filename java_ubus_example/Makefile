all: com.sbell.ubus.jar

com.sbell.ubus.jar:
	mkdir -p bin
	javac -d bin com/sbell/ubus/*.java
	jar -cfm $@ META-INF/MANIFEST.MF -C bin/ .

