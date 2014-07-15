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
package org.sonar.server.component.persistence;

import org.apache.ibatis.session.SqlSession;
import org.sonar.api.ServerComponent;
import org.sonar.api.utils.System2;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.component.db.ComponentMapper;
import org.sonar.core.persistence.DaoComponent;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.db.BaseDao;

import java.util.Date;

/**
 * @since 4.3
 */
public class ComponentDao extends BaseDao<ComponentMapper, ComponentDto, String> implements ServerComponent, DaoComponent {

  public ComponentDao(System2 system) {
    super(ComponentMapper.class, system);
  }

  public ComponentDto getById(Long id, SqlSession session) {
    return getMapper(session).selectById(id);
  }

  public boolean existsById(Long id, SqlSession session) {
    return getMapper(session).countById(id) > 0;
  }

  private ComponentMapper getMapper(SqlSession session) {
    return session.getMapper(ComponentMapper.class);
  }

  @Override
  protected ComponentDto doGetNullableByKey(DbSession session, String key) {
    return getMapper(session).selectByKey(key);
  }

  @Override
  protected ComponentDto doInsert(DbSession session, ComponentDto item) {
    getMapper(session).insert(item);
    return item;
  }

  @Override
  protected ComponentDto doUpdate(DbSession session, ComponentDto item) {
    throw notImplemented();
  }

  @Override
  protected void doDeleteByKey(DbSession session, String key) {
    throw notImplemented();
  }

  @Override
  public void synchronizeAfter(DbSession session, Date timestamp) {
    throw notImplemented();
  }

  private static IllegalStateException notImplemented() {
    throw new IllegalStateException("Not implemented yet");
  }
}