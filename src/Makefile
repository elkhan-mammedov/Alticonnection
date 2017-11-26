JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
        Client.java \
        AbstractClient.java \
        Downloader.java \
        Terminator.java \
        Uploader.java \
        Packet.java \
        Server.java \

default: classes

all: classes

classes: $(CLASSES:.java=.class)
	 chmod +x client
	 chmod +x server

clean:
	$(RM) *.class 