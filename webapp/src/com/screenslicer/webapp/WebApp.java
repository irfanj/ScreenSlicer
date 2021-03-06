/* 
 * ScreenSlicer (TM) -- automatic, zero-config web scraping (TM)
 * Copyright (C) 2013-2015 Machine Publishers, LLC
 * ops@machinepublishers.com | screenslicer.com | machinepublishers.com
 * Cincinnati, Ohio, USA
 *
 * You can redistribute this program and/or modify it under the terms of the
 * GNU Affero General Public License version 3 as published by the Free
 * Software Foundation. Additional permissions or commercial licensing may be
 * available--see LICENSE file or contact Machine Publishers, LLC for details.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License version 3
 * for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * version 3 along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * For general details about how to investigate and report license violations,
 * please see: https://www.gnu.org/licenses/gpl-violation.html
 * and email the author: ops@machinepublishers.com
 * Keep in mind that paying customers have more rights than the AGPL alone offers.
 */
package com.screenslicer.webapp;

import java.net.URI;
import java.security.SecureRandom;

import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import com.screenslicer.common.Config;
import com.screenslicer.common.Log;

public class WebApp extends ResourceConfig {

  public static interface Callback {
    void call();
  }

  private static ExceptionListener listener = null;
  private static final Object listenerLock = new Object();
  public static URI INTERNAL_URL;
  public static final boolean DEV = "true".equals(System.getProperty("slicer.dev", "false"));
  public static final boolean DEBUG = "true".equals(System.getProperty("slicer.debug", "false"));
  public static final boolean SANDBOX = "true".equals(System.getProperty("slicer.sandbox", "false"));
  public static final SecureRandom rand = new SecureRandom();
  public static final int THREADS = Integer.parseInt(System.getProperty("slicer.threads", "1"));
  private static final int MAX_REQUESTS = 1048576;

  private WebApp() {}

  public static void main(String[] args) throws Exception {}

  static void exception() {
    synchronized (listenerLock) {
      if (listener != null) {
        listener.exception();
      }
    }
  }

  public static synchronized void start(String name, int port,
      boolean useLoopback, ExceptionListener listener, Callback callback) {
    synchronized (listenerLock) {
      WebApp.listener = listener;
    }
    try {
      Log.init(name, !WebApp.DEV);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
    Config.instance.init();
    try {
      if (DEV || useLoopback) {
        INTERNAL_URL = URI.create("http://127.0.0.1:" + port);
      } else {
        INTERNAL_URL = URI.create("http://0.0.0.0:" + port);
      }
      Log.info("starting");
      if (callback != null) {
        callback.call();
      }
      WebAppConfig config = new WebAppConfig();
      HttpServer httpServer =
          GrizzlyHttpServerFactory.createHttpServer(INTERNAL_URL, config, false);
      CompressionConfig compressionConfig =
          httpServer.getListener("grizzly").getCompressionConfig();
      compressionConfig.setCompressionMode(CompressionConfig.CompressionMode.ON);
      compressionConfig.setCompressionMinSize(1);
      compressionConfig.setCompressableMimeTypes(config.mimeTypes());
      httpServer.getListener("grizzly").getTransport().
          getWorkerThreadPoolConfig().setMaxPoolSize(MAX_REQUESTS);
      httpServer.getListener("grizzly").getTransport().
          getWorkerThreadPoolConfig().setQueueLimit(MAX_REQUESTS);
      httpServer.getListener("grizzly").getTransport().
          getKernelThreadPoolConfig().setMaxPoolSize(MAX_REQUESTS);
      httpServer.getListener("grizzly").getTransport().
          getKernelThreadPoolConfig().setQueueLimit(MAX_REQUESTS);
      httpServer.start();
      final Object lock = new Object();
      synchronized (lock) {
        lock.wait();
      }
    } catch (Throwable t) {
      Log.exception(t);
      t.printStackTrace();
      System.exit(2);
    }
  }
}