/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.enterprise.helpers;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.neo4j.doc.server.helpers.CommunityServerBuilder;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.factory.GraphDatabaseFacadeFactory;
import org.neo4j.logging.LogProvider;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.enterprise.OpenEnterpriseNeoServer;
import org.neo4j.server.rest.web.DatabaseActions;

public class EnterpriseServerBuilder extends CommunityServerBuilder
{
    protected EnterpriseServerBuilder( LogProvider logProvider )
    {
        super( logProvider );
    }

    public static EnterpriseServerBuilder server()
    {
        return server( NullLogProvider.getInstance() );
    }

    public static EnterpriseServerBuilder server( LogProvider logProvider )
    {
        return new EnterpriseServerBuilder( logProvider );
    }

    @Override
    public OpenEnterpriseNeoServer build() throws IOException
    {
        return (OpenEnterpriseNeoServer) super.build();
    }

    @Override
    public EnterpriseServerBuilder usingDataDir( String dataDir )
    {
        super.usingDataDir( dataDir );
        return this;
    }

    @Override
    protected CommunityNeoServer build(Optional<File> configFile, Config config, GraphDatabaseFacadeFactory.Dependencies dependencies)
    {
        return new TestEnterpriseNeoServer( config, configFile, dependencies, logProvider );
    }

    private class TestEnterpriseNeoServer extends OpenEnterpriseNeoServer
    {
        private final Optional<File> configFile;

        public TestEnterpriseNeoServer( Config config, Optional<File> configFile, GraphDatabaseFacadeFactory.Dependencies dependencies, LogProvider logProvider )
        {
            super( config, dependencies, logProvider );
            this.configFile = configFile;
        }

        @Override
        protected DatabaseActions createDatabaseActions()
        {
            return createDatabaseActionsObject( database, getConfig() );
        }

        @Override
        public void stop()
        {
            super.stop();
            configFile.ifPresent( File::delete );
        }
    }
}
