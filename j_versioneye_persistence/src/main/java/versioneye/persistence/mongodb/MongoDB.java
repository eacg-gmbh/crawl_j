package versioneye.persistence.mongodb;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: reiz
 * Date: 1/1/12
 * Time: 6:41 PM
 *
 */
public class MongoDB {

    private DB db;
    private String host;
    private Integer port;
    private String host2;
    private Integer port2;
    private String host3;
    private Integer port3;
    private String dbname;
    private String username;
    private String password;

    public MongoDB(){
        System.out.println("init MongoDB");
    }

    public DB getDb(){
        if (db == null){
            initDB();
        }
        return db;
    }

    public synchronized void initDB(){
        try {
            Mongo mongo = null;

            String db_host = System.getenv("DB_PORT_27017_TCP_ADDR");
            String db_port = System.getenv("DB_PORT_27017_TCP_PORT");

            System.out.println("host/port: " + db_host + ":" + db_port);

            if (db_host != null && !db_host.isEmpty() && db_port != null && !db_port.isEmpty()){
                host = db_host;
                port = new Integer(db_port);
                host2 = null;
                host3 = null;
            }

            String env = System.getenv("RAILS_ENV");
            if (env != null && !env.isEmpty()){
                dbname = "veye_" + env;
            }
            System.out.println("dbname: " + dbname);

            if (host2 != null && !host2.isEmpty() && host3 != null && !host3.isEmpty()){
                List replicaset = new ArrayList();
                replicaset.add(new ServerAddress(host, port));
                replicaset.add(new ServerAddress(host2, port2));
                replicaset.add(new ServerAddress(host3, port3));
                mongo = new Mongo(replicaset);
            } else {
                mongo = new Mongo(host, port);
            }

            db = mongo.getDB(dbname);
            if (username != null && password != null && !username.trim().equals("") && !password.trim().equals("")){
                boolean auth = db.authenticate(username, password.toCharArray());
                System.out.println("auth: " + auth);
            }
            db.setReadPreference(ReadPreference.primary());
            System.out.println("getDB .. db is null .. create new db connection. MongoDB: " + this.toString() + " db: " + db.toString() );
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost2() {
        return host2;
    }

    public void setHost2(String host2) {
        this.host2 = host2;
    }

    public int getPort2() {
        return port2;
    }

    public void setPort2(int port2) {
        this.port2 = port2;
    }

    public String getHost3() {
        return host3;
    }

    public void setHost3(String host3) {
        this.host3 = host3;
    }

    public int getPort3() {
        return port3;
    }

    public void setPort3(int port3) {
        this.port3 = port3;
    }

    public void setDbname(String dbname) {
        this.dbname = dbname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}