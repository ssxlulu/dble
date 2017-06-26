package io.mycat.config.loader.zkprocess.zktoxml.listen;

import io.mycat.MycatServer;
import io.mycat.config.loader.zkprocess.comm.NotifyService;
import io.mycat.config.loader.zkprocess.comm.ZkConfig;
import io.mycat.config.loader.zkprocess.comm.ZkParamCfg;
import io.mycat.config.loader.zkprocess.comm.ZookeeperProcessListen;
import io.mycat.config.loader.zkprocess.zookeeper.DiretoryInf;
import io.mycat.config.loader.zkprocess.zookeeper.process.ConfStatus;
import io.mycat.config.loader.zkprocess.zookeeper.process.ZkDataImpl;
import io.mycat.config.loader.zkprocess.zookeeper.process.ZkDirectoryImpl;
import io.mycat.config.loader.zkprocess.zookeeper.process.ZkMultLoader;
import io.mycat.manager.response.ReloadConfig;
import io.mycat.manager.response.RollbackConfig;
import io.mycat.util.KVPathUtil;
import io.mycat.util.ZKUtils;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by huqing.yan on 2017/6/23.
 */
public class ConfigStatusListener extends ZkMultLoader implements NotifyService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BinlogPauseStatusListener.class);
	private final String currZkPath;
	private Set<NotifyService> childService = new HashSet<>();
	public ConfigStatusListener(ZookeeperProcessListen zookeeperListen, CuratorFramework curator) {
		this.setCurator(curator);
		currZkPath = KVPathUtil.getConfStatusPath();
		zookeeperListen.addWatch(currZkPath, this);
	}
	public void addChild(NotifyService service){
		childService.add(service);
	}
	@Override
	public boolean notifyProcess() throws Exception {
		if (MycatServer.getInstance().getProcessors() != null) {
			// 通过组合模式进行zk目录树的加载
			DiretoryInf StatusDirectory = new ZkDirectoryImpl(currZkPath, null);
			// 进行递归的数据获取
			this.getTreeDirectory(currZkPath, KVPathUtil.CONF_STATUS, StatusDirectory);
			// 从当前的下一级开始进行遍历,获得到
			ZkDataImpl zkDdata = (ZkDataImpl) StatusDirectory.getSubordinateInfo().get(0);
			ConfStatus status = new ConfStatus(zkDdata.getDataValue());
			if(status.getFrom().equals(ZkConfig.getInstance().getValue(ZkParamCfg.ZK_CFG_MYID))) {
				return true; //self node
			}
			LOGGER.info("ConfigStatusListener notifyProcess zk to object  :" + status);
			if (status.getStatus() == ConfStatus.Status.ROLLBACK){
				RollbackConfig.rollback();
				ZKUtils.createTempNode(KVPathUtil.getConfStatusPath(), ZkConfig.getInstance().getValue(ZkParamCfg.ZK_CFG_MYID));
				return true;
			}
			for (NotifyService service : childService) {
				try {
					service.notifyProcess();
				} catch (Exception e) {
					LOGGER.error("ConfigStatusListener notify  error :" + service + " ,Exception info:", e);
				}
			}
			if (status.getStatus() == ConfStatus.Status.RELOAD_ALL) {
				ReloadConfig.reload_all();
			} else {
				ReloadConfig.reload();
			}
			ZKUtils.createTempNode(KVPathUtil.getConfStatusPath(), ZkConfig.getInstance().getValue(ZkParamCfg.ZK_CFG_MYID));
		}
		return true;
	}
}
