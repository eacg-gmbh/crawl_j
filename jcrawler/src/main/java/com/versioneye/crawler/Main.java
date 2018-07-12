package com.versioneye.crawler;

import com.versioneye.crawler.crawl.ICrawl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.domain.Repository;
import com.versioneye.persistence.mongodb.MongoDB;
import com.versioneye.utils.LogUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class Main {

    static final Logger logger = LogManager.getLogger(Main.class.getName());

    private static final long SECOND = 1000L;
    private static ApplicationContext context;
    private static LogUtils logUtils;

    public static void main(String[] args){
        context = new ClassPathXmlApplicationContext("applicationContext.xml");
        logUtils = (LogUtils) context.getBean("logUtils");

        String daemon       = getParam(args, 0);
        String crawler      = getParam(args, 1);
        String sleepHour    = getParam(args, 2);
        String package_name = getParam(args, 3);

        MongoDB mongoDB = (MongoDB) context.getBean("mongoDb");
        mongoDB.setUsername("veuser");
        mongoDB.setPassword("veecs");
        mongoDB.initDB();

        execute( crawler, package_name );

        if (!daemon.equals("-d"))
            return ;

        for (;;){
            sleepAWhile( Integer.parseInt( sleepHour ) );
            execute( crawler, package_name );
        }
    }

    private static void execute( String crawlerName, String package_name ){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        String execGroup = sdf.format(new Date());
        ICrawl crawler = (ICrawl) context.getBean(crawlerName);
        startCrawlerWithAllRepos( crawler, crawlerName, execGroup, package_name );
    }

    private static void startCrawlerWithAllRepos( ICrawl crawlerOne, String crawlerName, String execGroup, String packageName ){
        for ( Repository repo : crawlerOne.getRepositories() ){
            ICrawl crawler = (ICrawl) context.getBean(crawlerName); // init a new object because of threading!
            startCrawler(crawler, repo, execGroup, packageName);
        }
    }

    private static void startCrawler(ICrawl crawler, Repository repository, String execGroup, String packageName){
        crawler.setRepository(repository);
        crawler.setExecGroup(execGroup);
        if (crawler.isThreadable()){
            startThreads(crawler, execGroup);
        } else {
            if (packageName == null || packageName.trim().equals(""))
                crawler.crawl();
            else
                crawler.crawlePackage(packageName);
        }
    }

    private static void startThreads(ICrawl crawler, String execGroup) {
        Set<String> list = crawler.getFirstLevelList();
        if (list == null || list.isEmpty())
            return ;
        List<String> done = new ArrayList<String>();

        String crawlerName = crawler.getName();
        Repository repository = crawler.getRepository();
        int z = 0;
        int threadAmount = 15;
        Thread[] threads = new Thread[threadAmount];

        for (String id: list){
            Thread thread = startThread(crawlerName, repository, id, execGroup);
            threads[z++] = thread;
            done.add(id);
            logger.info("Start new Thread " + thread.getName());
            if (z == threadAmount)
                break;
        }

        try{
            for (;;){
                int threadsAlive = 0;
                for (int i = 0; i < threads.length; i++ ){
                    Thread thread = threads[i];
                    if (thread == null){
                        continue;
                    }
                    if (thread.isAlive()){
                        threadsAlive++;
                        continue;
                    }
                    thread = getNextThread(list, done, crawlerName, repository, execGroup);
                    if (thread != null){
                        threads[i] = thread;
                        logger.info("Start new Thread " + thread.getName());
                    }
                }
                if (threadsAlive == 0)
                    break;
            }
        } catch (Exception ex){
            logger.info("error in startThreads()", ex.getMessage());
        }
    }

    private static synchronized Thread getNextThread(Set<String> list, List<String> done, String name, Repository repository, String execGroup){
        for (String id: list){
            if (done.contains(id))
                continue;
            done.add(id);
            return startThread(name, repository, id, execGroup);
        }
        return null;
    }

    private static Thread startThread(String beanName, Repository repository, String id, String execGroup){
        ICrawl crawler = (ICrawl) context.getBean(beanName);
        crawler.setRepository(repository);
        crawler.setStartPoint(id);
        crawler.setExecGroup(execGroup);
        Thread thread = new Thread(crawler);
        thread.start();
        return thread;
    }

    private static void sleepAWhile(int hours){
        try{
            long minute = SECOND * 60;
            long hour = minute * 60;
            Thread.sleep(hour * hours);
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private static String getParam(String[] args, int number){
        String param = "";
        if (args != null && args.length > number)
            param = args[number];
        return param;
    }

}
