package info.kgeorgiy.ja.tkachenko.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler {
    private final int perHost;
    private final Downloader downloader;
    private final ExecutorService downloadersPool;
    private final ExecutorService extractorsPool;


    private class Host {
        int cnt;
        final Queue<Runnable> waiting;

        Host() {
            waiting = new ArrayDeque<>();
            cnt = 0;
        }

        synchronized void addTask(Runnable task) {
            if (cnt < perHost) {
                cnt++;
                downloadersPool.submit(task);
            } else {
                waiting.add(task);
            }
        }

        synchronized void nextTask() {
            if (waiting.isEmpty()) {
                cnt--;
            } else {
                downloadersPool.submit(waiting.poll());
            }
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadersPool = Executors.newFixedThreadPool(downloaders);
        extractorsPool = Executors.newFixedThreadPool(extractors);
    }

    // RESOLVED :NOTE: download should be concurrent
    @Override
    public Result download(String url, int depth) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> downloaded = ConcurrentHashMap.newKeySet();
        Map<String, IOException> errors = new ConcurrentHashMap<>();
        Phaser phaser = new Phaser(1);
        ConcurrentMap<String, Host> hosts = new ConcurrentHashMap<>();

        visited.add(url);
        crawl(url, depth, visited, downloaded, errors, phaser, hosts);
        phaser.arriveAndAwaitAdvance();
        return new Result(new ArrayList<>(downloaded), errors);
    }

    private void crawl(final String url, final int depth, Set<String> visited, Set<String> downloaded,
                       Map<String, IOException> errors, Phaser phaser, ConcurrentMap<String, Host> hosts) {
        final String host;
        try {
            host = URLUtils.getHost(url);
        } catch (MalformedURLException e) {
            errors.put(url, e);
            return;
        }

        final Host data = hosts.computeIfAbsent(host, hostData -> new Host());
        phaser.register();
        data.addTask(() -> {
            try {
                Document document = downloader.download(url);
                downloaded.add(url);

                if (depth > 1) {
                    phaser.register();
                    extractorsPool.submit(() -> {
                        try {
                            document.extractLinks().stream()
                                    .parallel()
                                    .filter(visited::add)
                                    .forEach(link -> crawl(link, depth - 1, visited, downloaded, errors, phaser, hosts));
                        } catch (IOException e) {
                            errors.put(url, e);
                        } finally {
                            phaser.arriveAndDeregister();
                        }
                    });
                }
            } catch (IOException e) {
                errors.put(url, e);
            } finally {
                data.nextTask();
                phaser.arriveAndDeregister();
            }
        });
    }

    @Override
    public void close() {
        downloadersPool.shutdown();
        extractorsPool.shutdown();
        try {
            if (!downloadersPool.awaitTermination(60, TimeUnit.SECONDS)
                    || !extractorsPool.awaitTermination(60, TimeUnit.SECONDS)) {
                downloadersPool.shutdownNow();
                extractorsPool.shutdownNow();
                if (!downloadersPool.awaitTermination(60, TimeUnit.SECONDS)
                        || !extractorsPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Error: failed to terminate thread pools");
                }
            }
        } catch (InterruptedException e) {
            downloadersPool.shutdownNow();
            extractorsPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        // args.length 1..5
        if (args == null || args.length < 1 || args.length > 5) {
            System.err.println("The correct usage is: WebCrawler <url> [depth [downloads [extractors [perHost]]]]>");
            return;
        }
        int n = args.length;
        int[] intArgs = new int[n - 1];
        for (int i = 1; i < 5; i++) {
            try {
                if (i < n) {
                    intArgs[i - 1] = Integer.parseInt(args[i]);
                } else {
                    intArgs[i - 1] = 8;
                }
            } catch (NumberFormatException e) {
                System.err.println("Arguments should be integer value: " + e.getMessage());
                return;
            }
        }
        try (Crawler crawler = new WebCrawler(new CachingDownloader(), intArgs[1], intArgs[2], intArgs[3])) {
            crawler.download(args[0], intArgs[0]);
        } catch (IOException e) {
            System.err.println("Error while creating WebCrawler: " + e.getMessage());
        }
    }
}
