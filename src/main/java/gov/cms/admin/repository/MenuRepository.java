package gov.cms.admin.repository;

import gov.cms.admin.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByOrderBySortAscIdAsc();

    @Query("SELECT m FROM Menu m WHERE m.visible = true AND m.status = 'enabled' ORDER BY m.sort ASC, m.id ASC")
    List<Menu> findVisibleMenus();

    @Query("SELECT m FROM Menu m WHERE m.id IN :ids AND m.visible = true AND m.status = 'enabled' ORDER BY m.sort ASC, m.id ASC")
    List<Menu> findByIdInAndVisible(@Param("ids") List<Long> ids);

    boolean existsByPermissionId(String permissionId);

    Optional<Menu> findByPermissionId(String permissionId);

    Optional<Menu> findByPath(String path);
}
