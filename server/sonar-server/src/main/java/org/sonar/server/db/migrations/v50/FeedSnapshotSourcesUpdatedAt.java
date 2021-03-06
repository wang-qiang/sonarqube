/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.db.migrations.v50;

import org.sonar.core.persistence.Database;
import org.sonar.server.db.migrations.BaseDataChange;
import org.sonar.server.db.migrations.MassUpdate;
import org.sonar.server.db.migrations.Select;
import org.sonar.server.db.migrations.SqlStatement;

import java.sql.SQLException;

/**
 * Used in the Active Record Migration 712
 *
 * @since 5.0
 */
public class FeedSnapshotSourcesUpdatedAt extends BaseDataChange {

  public FeedSnapshotSourcesUpdatedAt(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("SELECT ss.id, s.build_date " +
      "FROM snapshot_sources ss " +
      "INNER JOIN snapshots s ON s.id = ss.snapshot_id " +
      "WHERE ss.updated_at IS NULL");
    massUpdate.update("UPDATE snapshot_sources " +
      "SET updated_at=? " +
      "WHERE id=?");
    massUpdate.execute(new MassUpdate.Handler() {
      @Override
      public boolean handle(Select.Row row, SqlStatement update) throws SQLException {
        update.setDate(1, row.getDate(2));
        update.setLong(2, row.getLong(1));
        return true;
      }
    });
  }
}
