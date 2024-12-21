package util;

import java.text.SimpleDateFormat;

public class Logger {
    //static means that it can be accessed without creating an instance of the class
    private static final String Red = "\u001b[31m";
    private static final String Green = "\u001b[32m";
    private static final String Yellow = "\u001b[33m";
    private static final String Blue = "\u001b[34m";
    private static final String Magenta = "\u001b[35m";
    private static final String Cyan = "\u001b[36m";
    private static final String Reset = "\u001b[0m";

    private static final SimpleDateFormat dateFormat= new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

    public static void log(String message) {
        log(message, LogLevel.Info);
    }
    public static void log(String message, LogLevel level) {
        String dateString = dateFormat.format(System.currentTimeMillis());
        String threadName = Thread.currentThread().getName();
        String finalString = "[" + dateString + "][" + threadName + "] "+ level + ": " ;
        switch (level){
            case Debug -> finalString = Blue + finalString + Reset + message;
            case Info -> finalString = Yellow + finalString + Reset + message;
            case Warn -> finalString = Magenta + finalString + Reset + message;
            case Error -> finalString = Red + finalString + Reset + message;
            case Success -> finalString = Green + finalString + Reset + message;
            case Status -> finalString = Cyan + finalString + Reset + message;
        }
        System.out.println(finalString);
    }

}
