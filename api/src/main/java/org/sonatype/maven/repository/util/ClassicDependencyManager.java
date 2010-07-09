package org.sonatype.maven.repository.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.sonatype.maven.repository.Artifact;
import org.sonatype.maven.repository.Dependency;
import org.sonatype.maven.repository.DependencyManagement;
import org.sonatype.maven.repository.DependencyManager;
import org.sonatype.maven.repository.DependencyNode;
import org.sonatype.maven.repository.Exclusion;

/**
 * A dependency manager that mimics the way Maven 2.x works.
 * 
 * @author Benjamin Bentmann
 */
public class ClassicDependencyManager
    implements DependencyManager
{

    private final int depth;

    private final Map<Object, String> managedVersions;

    private final Map<Object, String> managedScopes;

    private final Map<Object, Collection<Exclusion>> managedExclusions;

    /**
     * Creates a new dependency manager without any management information.
     */
    public ClassicDependencyManager()
    {
        this( 0, Collections.<Object, String> emptyMap(), Collections.<Object, String> emptyMap(),
              Collections.<Object, Collection<Exclusion>> emptyMap() );
    }

    private ClassicDependencyManager( int depth, Map<Object, String> managedVersions,
                                      Map<Object, String> managedScopes,
                                      Map<Object, Collection<Exclusion>> managedExclusions )
    {
        this.depth = depth;
        this.managedVersions = managedVersions;
        this.managedScopes = managedScopes;
        this.managedExclusions = managedExclusions;
    }

    public DependencyManager deriveChildManager( DependencyNode node, List<? extends Dependency> managedDependencies )
    {
        if ( depth >= 2 )
        {
            return this;
        }
        else if ( depth == 1 )
        {
            return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedExclusions );
        }

        Map<Object, String> managedVersions = this.managedVersions;
        Map<Object, String> managedScopes = this.managedScopes;
        Map<Object, Collection<Exclusion>> managedExclusions = this.managedExclusions;

        for ( Dependency managedDependency : managedDependencies )
        {
            Artifact artifact = managedDependency.getArtifact();
            Object key = getKey( artifact );

            String version = artifact.getVersion();
            if ( version.length() > 0 && !managedVersions.containsKey( key ) )
            {
                if ( managedVersions == this.managedVersions )
                {
                    managedVersions = new HashMap<Object, String>( this.managedVersions );
                }
                managedVersions.put( key, version );
            }

            String scope = managedDependency.getScope();
            if ( scope.length() > 0 && !managedScopes.containsKey( key ) )
            {
                if ( managedScopes == this.managedScopes )
                {
                    managedScopes = new HashMap<Object, String>( this.managedScopes );
                }
                managedScopes.put( key, scope );
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if ( !exclusions.isEmpty() )
            {
                if ( managedExclusions == this.managedExclusions )
                {
                    managedExclusions = new HashMap<Object, Collection<Exclusion>>( this.managedExclusions );
                }
                Collection<Exclusion> managed = managedExclusions.get( key );
                if ( managed == null )
                {
                    managed = new LinkedHashSet<Exclusion>();
                    managedExclusions.put( key, managed );
                }
                managed.addAll( exclusions );
            }
        }

        return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedExclusions );
    }

    public DependencyManagement manageDependency( Dependency dependency )
    {
        DependencyManagement management = null;

        Object key = getKey( dependency.getArtifact() );

        if ( depth >= 2 )
        {
            String version = managedVersions.get( key );
            if ( version != null )
            {
                if ( management == null )
                {
                    management = new DependencyManagement();
                }
                management.setVersion( version );
            }

            String scope = managedScopes.get( key );
            if ( scope != null )
            {
                if ( management == null )
                {
                    management = new DependencyManagement();
                }
                management.setScope( scope );
            }
        }

        Collection<Exclusion> exclusions = managedExclusions.get( key );
        if ( exclusions != null )
        {
            if ( management == null )
            {
                management = new DependencyManagement();
            }
            Collection<Exclusion> result = new LinkedHashSet<Exclusion>( dependency.getExclusions() );
            result.addAll( exclusions );
            management.setExclusions( result );
        }

        return management;
    }

    private Object getKey( Artifact a )
    {
        return new Key( a );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        else if ( null == obj || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        ClassicDependencyManager that = (ClassicDependencyManager) obj;
        return depth == that.depth && managedVersions.equals( that.managedVersions )
            && managedScopes.equals( that.managedScopes ) && managedExclusions.equals( that.managedExclusions );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + depth;
        hash = hash * 31 + managedVersions.hashCode();
        hash = hash * 31 + managedScopes.hashCode();
        hash = hash * 31 + managedExclusions.hashCode();
        return hash;
    }

    static class Key
    {

        private final Artifact artifact;

        private final int hashCode;

        public Key( Artifact artifact )
        {
            this.artifact = artifact;

            int hash = 17;
            hash = hash * 31 + artifact.getGroupId().hashCode();
            hash = hash * 31 + artifact.getArtifactId().hashCode();
            hashCode = hash;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof Key ) )
            {
                return false;
            }
            Key that = (Key) obj;
            return artifact.getArtifactId().equals( that.artifact.getArtifactId() )
                && artifact.getGroupId().equals( that.artifact.getGroupId() )
                && artifact.getExtension().equals( that.artifact.getExtension() )
                && artifact.getClassifier().equals( that.artifact.getClassifier() );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

    }

}