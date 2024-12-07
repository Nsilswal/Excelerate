**Welcome to Excelerate!**

Excelerate is a cool personal project of mine which - pushes the limit of viewing csv data - . Microsoft excel has a limit of 1,048,576 Rows. This project should be able to handle an infinite number of rows and run quickly on legacy hardware.

This is acomplished by loading the data into a sql database and performing pagination - behind the scenes we load 'pages' of data as you scroll improving speed. This is probably one of my favorite features, as to the user it seems infinite when in reality we are paginating.

To build and run the project:

```
mvn clean package
java -jar target/Excelerate-1.0-SNAPSHOT-jar-with-dependencies.jar
```

**Future Features:**

- I'm currently working on a translation engine to translate complex excel commands into sql. Actions like selecting mulitple rows and calculating their average or similar.
-

**Design Choices:**

- We use Java because its fast and has a good standard library. Python is too slow for this type of project. I originally wanted to use Rust, but that was harder to use because GUI programming was not as easy.
