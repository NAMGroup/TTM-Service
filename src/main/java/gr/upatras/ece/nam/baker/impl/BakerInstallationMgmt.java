/**
 * Copyright 2014 University of Patras 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * See the License for the specific language governing permissions and limitations under the License.
 */

package gr.upatras.ece.nam.baker.impl;

import gr.upatras.ece.nam.baker.model.IRepositoryWebClient;
import gr.upatras.ece.nam.baker.model.InstalledBun;
import gr.upatras.ece.nam.baker.model.InstalledBunStatus;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BakerInstallationMgmt {

	private ConcurrentHashMap<String, InstalledBun> managedInstalledBuns;
	private IRepositoryWebClient repoWebClient;
	private BakerJpaController bakerJpaController;

	private static final transient Log logger = LogFactory.getLog(BakerInstallationMgmt.class.getName());

	public BakerInstallationMgmt() {
		managedInstalledBuns = new ConcurrentHashMap<>();
		// this.setRepoWebClient(new RepositoryWebClient());
	}

	public ConcurrentHashMap<String, InstalledBun> getManagedInstalledBuns() {
		return managedInstalledBuns;
	}

	/**
	 * Add installed service object to ManagedServices list
	 * 
	 * @param s
	 *            InstalledBun to add
	 * @return the same service
	 */
	private InstalledBun addBunToManagedBuns(InstalledBun s) {
		managedInstalledBuns.put(s.getUuid(), s);


		return s;
	}

//	private Boolean removeServiceFromManagedServices(InstalledBun s) {
//		InstalledBun is = managedInstalledBuns.remove(s.getUuid());
//		bakerJpaController.delete(s);
//		return (is != null);
//	}

	/**
	 * Starts the installation of a bun. If found already in local registry (managedInstalledBun list) returns a ref to an existing instance if found. Otherwise
	 * starts a new installation.
	 * 
	 * @param reqBunUUID
	 *            The uuid of the requested Bun
	 * @param bunRepoURL
	 *            The endpoint of the repository
	 * @return an InstalledBun object
	 */
	public InstalledBun installBunAndStart(String reqBunUUID, String bunRepoURL) {

		logger.info("reqBunUUID= " + reqBunUUID+", bunRepoURL= "+ bunRepoURL);
		InstalledBun s = managedInstalledBuns.get(reqBunUUID); // return existing if
														// found
		if ((s != null) && (s.getStatus() != InstalledBunStatus.FAILED)
				 && (s.getStatus() != InstalledBunStatus.INIT)
				  && (s.getStatus() != InstalledBunStatus.DOWNLOADING)
				  && (s.getStatus() != InstalledBunStatus.DOWNLOADED)
				  && (s.getStatus() != InstalledBunStatus.UNINSTALLED)) {
			return s;
		}

		logger.info("will start installation");

		if (s == null) {
			s = new InstalledBun(reqBunUUID, bunRepoURL);
			addBunToManagedBuns(s);
			bakerJpaController.saveInstalledBun(s);
		} else if ((s.getStatus() == InstalledBunStatus.FAILED) ||
				(s.getStatus() == InstalledBunStatus.INIT) ||
				(s.getStatus() == InstalledBunStatus.DOWNLOADING) ||
				(s.getStatus() == InstalledBunStatus.DOWNLOADED) ||(s.getStatus() == InstalledBunStatus.UNINSTALLED)) {

			logger.info("Will RESTART installation of existing " + s.getUuid() + ". HAD Status= " + s.getStatus());
			s.setStatus(InstalledBunStatus.INIT); // restart installation
			s.setRepoUrl(bunRepoURL);
		}

		processBunLifecycleJob(s, this.bakerJpaController, InstalledBunStatus.STARTED);
		return s;
	}

	/**
	 * It executes the installation of the bun in a thread job, following the bun installation state resource
	 * 
	 * @param s
	 *            InstalledBun object to manage the lifecycle
	 */
	private void processBunLifecycleJob(final InstalledBun s, final BakerJpaController jpsctr, final InstalledBunStatus targetStatus) {

		logger.info("Creating new thread of " + s.getUuid() + " for target action = " + targetStatus);
		Thread t1 = new Thread(new Runnable() {
			public void run() {

				new InstalledBunLifecycleMgmt(s, repoWebClient, jpsctr, targetStatus);

			}
		});
		t1.start();

		// Runnable run = new InstallationTask(s, repoWebClient);
		// Thread thread = new Thread(run);
		// thread.start();

	}

	public InstalledBun getBun(String uuid) {
		InstalledBun is = managedInstalledBuns.get(uuid);
		return is;
	}

	public IRepositoryWebClient getRepoWebClient() {
		return repoWebClient;
	}

	public void setRepoWebClient(IRepositoryWebClient repoWebClient) {
		this.repoWebClient = repoWebClient;
	}

	public BakerJpaController getBakerJpaController() {
		return bakerJpaController;
	}

	public void setBakerJpaController(BakerJpaController b) {
		this.bakerJpaController = b;

		this.bakerJpaController = b;
		List<InstalledBun> ls = b.readInstalledBuns(0, 100000);

		for (InstalledBun installedBun : ls) {
			managedInstalledBuns.put(installedBun.getUuid(), installedBun);
		}
	}

	public void stopBun(String uuid) {
		InstalledBun is = managedInstalledBuns.get(uuid);

		if (is.getStatus() != InstalledBunStatus.STARTED)
			return;

		logger.info("will stop service uuid= " + uuid);


		processBunLifecycleJob(is, this.bakerJpaController, InstalledBunStatus.STOPPED);

	}
	
	public void startBun(String uuid) {
		InstalledBun is = managedInstalledBuns.get(uuid);

		if (is.getStatus() != InstalledBunStatus.STOPPED)
			return;

		logger.info("will start service uuid= " + uuid);


		processBunLifecycleJob(is, this.bakerJpaController, InstalledBunStatus.STARTED);

	}

	public void uninstallBun(String uuid) {
		InstalledBun is = managedInstalledBuns.get(uuid);

		logger.info("will uninstall bun uuid= " + uuid);

		processBunLifecycleJob(is, this.bakerJpaController, InstalledBunStatus.UNINSTALLED);

	}

	public void configureBun(String uuid) {
		InstalledBun is = managedInstalledBuns.get(uuid);

		logger.info("will configure bun uuid= " + uuid);

		processBunLifecycleJob(is, this.bakerJpaController, InstalledBunStatus.STARTED);

	}

}
