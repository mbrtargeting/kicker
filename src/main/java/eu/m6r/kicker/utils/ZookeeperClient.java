package eu.m6r.kicker.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ZookeeperClient {

    public static String ZOOKEEPER_ROOT_PATH = "/kicker";

    private final Logger logger;
    private final ZooKeeper zooKeeper;

    public ZookeeperClient(final String zookeeperHosts) throws IOException {
        this.logger = LogManager.getLogger();
        this.zooKeeper = new ZooKeeper(zookeeperHosts, 30000, null);
    }

    public void createPath(final String path) throws IOException {
        StringBuilder currentPath = new StringBuilder();

        final List<String> paths =
                Arrays.stream(path.split("/")).filter(p -> !p.isEmpty())
                        .collect(Collectors.toList());

        for (String subPath : paths) {
            currentPath.append("/").append(subPath);

            try {
                if (zooKeeper.exists(currentPath.toString(), false) == null) {
                    zooKeeper.create(currentPath.toString(), null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                     CreateMode.PERSISTENT);
                }
            } catch (InterruptedException | KeeperException e) {
                throw new IOException(e);
            }
        }
    }

    public String createEphemeralSequential(final String path, final String value)
            throws IOException {
        try {
            return zooKeeper.create(path, value.getBytes(),
                                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                    CreateMode.EPHEMERAL_SEQUENTIAL);
        } catch (KeeperException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    public boolean checkLock(final String lockPath, final Watcher watcher)
            throws KeeperException, InterruptedException {
        final String parentPath = Paths.get(lockPath).getParent().toString();

        final List<String> children = zooKeeper.getChildren(parentPath, watcher);
        return children.stream()
                .min(String::compareTo)
                .map(string -> {
                    logger.info("Current min lock node: {}", string);
                    return String.format("%s/%s", parentPath, string).equals(lockPath);
                })
                .orElse(false);
    }

    public void writeNode(final String path, final String value) throws IOException {
        try {
            final Stat stat = zooKeeper.exists(path, false);

            if (stat == null) {
                zooKeeper.create(path, value.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                 CreateMode.PERSISTENT);
            } else {
                zooKeeper.setData(path, value.getBytes(), stat.getVersion());
            }
        } catch (InterruptedException | KeeperException e) {
            throw new IOException(e);
        }
    }

    public String readNode(final String path) throws IOException {
        try {
            if (zooKeeper.exists(path, false) == null) {
                return null;
            }

            return new String(zooKeeper.getData(path, null, null));
        } catch (InterruptedException | KeeperException e) {
            throw new IOException(e);
        }
    }
}