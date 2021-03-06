/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.dropwizard.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class NetServerMetricsImpl extends AbstractMetrics implements TCPMetrics<Timer.Context> {

  private Counter openConnections;
  private Timer connections;
  private Histogram bytesRead;
  private Histogram bytesWritten;
  private Counter exceptions;
  protected volatile boolean closed;

  NetServerMetricsImpl(AbstractMetrics metrics, String baseName, SocketAddress localAddress) {
    super(metrics.registry(), localAddress != null ? (MetricRegistry.name(baseName, addressName(localAddress))) : (baseName));

    this.openConnections = counter("open-netsockets");
    this.connections = timer("connections");
    this.exceptions = counter("exceptions");
    this.bytesRead = histogram("bytes-read");
    this.bytesWritten = histogram("bytes-written");
  }

  @Override
  public void close() {
    this.closed = true;
    removeAll();
  }

  @Override
  public Timer.Context connected(SocketAddress remoteAddress) {
    // Connection metrics
    openConnections.inc();

    // Remote address connection metrics
    counter("open-connections", remoteAddress.host()).inc();

    // A little clunky, but it's possible we got here after closed has been called
    if (closed) {
      removeAll();
    }

    //
    return connections.time();
  }

  @Override
  public void disconnected(Timer.Context ctx, SocketAddress remoteAddress) {
    openConnections.dec();
    ctx.stop();

    // Remote address connection metrics
    Counter counter = counter("open-connections", remoteAddress.host());
    counter.dec();
    if (counter.getCount() == 0) {
      remove("open-connections", remoteAddress.host());
    }

    // A little clunky, but it's possible we got here after closed has been called
    if (closed) {
      removeAll();
    }
  }

  @Override
  public void bytesRead(Timer.Context socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    bytesRead.update(numberOfBytes);
  }

  @Override
  public void bytesWritten(Timer.Context socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    bytesWritten.update(numberOfBytes);
  }

  @Override
  public void exceptionOccurred(Timer.Context socketMetric, SocketAddress remoteAddress, Throwable t) {
    exceptions.inc();
  }

  protected long connections() {
    if (openConnections == null) return 0;

    return openConnections.getCount();
  }

  protected static String addressName(SocketAddress address) {
    if (address == null) return null;

    return address.host() + ":" + address.port();
  }
}
