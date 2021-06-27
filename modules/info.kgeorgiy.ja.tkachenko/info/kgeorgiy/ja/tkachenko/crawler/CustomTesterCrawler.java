package info.kgeorgiy.ja.tkachenko.crawler;

import info.kgeorgiy.java.advanced.crawler.Tester;
import info.kgeorgiy.java.advanced.crawler.EasyCrawlerTest;
import info.kgeorgiy.java.advanced.crawler.HardCrawlerTest;


public class CustomTesterCrawler extends Tester {

    public static void main(String[] args) {
        new CustomTesterCrawler()
                .add("EasyCrawler", EasyCrawlerTest.class)
                .add("HardCrawler", HardCrawlerTest.class)
                .run(args);
    }
}