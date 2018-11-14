all: WebServer.java clean app
app:
	javac WebServer.java
	ls -la
clean:
	rm -rf *.class