/*
 * The MIT License
 *
 * Copyright (c) 2020, Sumit Sarin and Jenkins project contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins.redis;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.model.FingerprintStorage;
import hudson.model.Job;
import hudson.model.Fingerprint;
import hudson.util.PersistedList;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import jenkins.model.FingerprintFacet;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import redis.clients.jedis.Jedis;

/**
 * Pluggable external fingerprint storage for fingerprints in Redis.
 */

@Extension
public class RedisFingerprintStorage extends FingerprintStorage {

    private final String instanceId;
    private static final Logger logger = Logger.getLogger(Fingerprint.class.getName());

    public RedisFingerprintStorage() throws IOException{
        try {
            instanceId = Util.getDigestOf(new ByteArrayInputStream(InstanceIdentity.get().getPublic().getEncoded()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to obtain Instance ID. "+e);
            throw e;
        }
    }

    /**
     * Saves the given fingerprint.
     */
    public synchronized void save(Fingerprint fp) throws IOException {
        if(BulkChange.contains(fp))
            return;

        Jedis jedis = new Jedis("localhost");

        StringWriter writer = new StringWriter();
        fp.getXStream().toXML(fp, writer);

        jedis.set(instanceId+fp.getHashString(), writer.toString());
    }

    public @CheckForNull Fingerprint load(@NonNull byte[] md5sum) throws IOException {
        return load(Util.toHexString(md5sum));
    }

    /**
     * Returns the fingerprint associated with the given md5sum and the Jenkins instance ID, from the storage.
     */
    public @CheckForNull Fingerprint load(@NonNull String md5sum) throws IOException {
        Jedis jedis = new Jedis("localhost");
        String db = jedis.get(instanceId+md5sum);

        if (db == null)
            return null;

        Object loaded = null;

        try (InputStream in = new ByteArrayInputStream(db.getBytes())) {
            loaded = Fingerprint.getXStream().fromXML(in);
        } catch (RuntimeException | Error e) {
            throw new IOException("Unable to read fingerprint.",e);
        }

        if (!(loaded instanceof Fingerprint)) {
            throw new IOException("Unexpected Fingerprint type. Expected " + Fingerprint.class + " or subclass but got "
                    + (loaded != null ? loaded.getClass() : "null"));
        }
        Fingerprint fingerprint = (Fingerprint) loaded;

        if (fingerprint.getPersistedFacets()==null)
            fingerprint.setPersistedFacets(new PersistedList<>(fingerprint));
        for (FingerprintFacet facet : fingerprint.getPersistedFacets())
            facet._setOwner(fingerprint);

        return fingerprint;
    }

    /**
     * Deletes the fingerprint fp with the associated md5 of the given fingerprint and jenkins instance ID.
     */
    public boolean delete(Fingerprint fp){
        return true;
    }

    /**
     * Deletes the fingerprint fp with the associated md5 of the given fingerprint and jenkins instance ID.
     */
    public boolean delete(byte[] md5sum){
        return true;
    }

    /**
     * Returns all the fingerprints associated with the given md5sum, across all Jenkins instances connected to the
     * external storage.
     */
    public Fingerprint[] trace(byte[] md5sum){
        return null;
    }

    /**
     * Returns all the fingerprints associated with the system’s Jenkins instance ID in the storage.
     */
    public Fingerprint[] getAllLocalFingerprints() {return null;}

    /**
     * Returns all the fingerprints stored in the storage.
     */
    public Fingerprint[] getAllGlobalFingerprints() {return null;}

    /**
     * Returns all the fingerprints associated with the Job j across all Jenkins instances.
     */
    public Fingerprint[] trace(Job j){
        return null;
    }

    /**
     * Returns all the fingerprints associated with given Job and build across all Jenkins instances.
     */
    public Fingerprint[] trace(Job j, int build){
        return null;
    }


}