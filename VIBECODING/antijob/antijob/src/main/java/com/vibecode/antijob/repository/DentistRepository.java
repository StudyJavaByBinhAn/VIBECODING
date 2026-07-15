package com.vibecode.antijob.repository;

import com.vibecode.antijob.entity.Dentist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DentistRepository extends JpaRepository<Dentist, Long> {
    List<Dentist> findByActiveTrue();

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

    // Khoá hàng dentist trước khi check trùng lịch: SELECT ... FOR UPDATE trên appointments
    // chỉ khoá được hàng ĐÃ TỒN TẠI, nên 2 request đặt cùng 1 slot còn trống (chưa có hàng nào
    // để khoá) có thể cùng pass qua check trùng và cùng insert. Khoá hẳn hàng dentist buộc
    // request thứ 2 phải chờ request thứ 1 commit/rollback trước khi tự check lại — lúc đó
    // hàng appointment của request 1 (nếu đã insert) chắc chắn đã hiện diện để request 2 thấy.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dentist d WHERE d.id = :id")
    Optional<Dentist> findByIdForUpdate(@Param("id") Long id);
}
