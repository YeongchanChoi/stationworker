package com.example.subway.service;

import com.example.subway.domain.User;
import com.example.subway.dto.UserRegistrationRequest;
import com.example.subway.dto.UserLoginRequest;
import com.example.subway.repository.UserRepository;
import com.example.subway.repository.SubwayStationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final SubwayStationRepository subwayStationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       SubwayStationRepository subwayStationRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subwayStationRepository = subwayStationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 회원가입 처리: 입력받은 근무역이 subway_stations 테이블에 존재하는지 확인
    public User registerUser(UserRegistrationRequest request) {
        // existsByStationname() 메서드를 사용하여 역 존재 여부를 확인
        if (!subwayStationRepository.existsByStationname(request.getWorkStation())) {
            throw new RuntimeException("입력하신 근무역이 존재하지 않습니다.");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        // 비밀번호는 암호화하여 저장
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setWorkStation(request.getWorkStation());
        user.setAlertThreshold(request.getAlertThreshold());
        return userRepository.save(user);
    }

    // 로그인 인증 (아이디/비밀번호 확인)
    public Optional<User> authenticate(UserLoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isPresent() && passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return userOpt;
        }
        return Optional.empty();
    }

    // 프로필(근무역, 알림 정거장 수) 업데이트: 변경 시에도 근무역의 존재 여부를 검사
    public User updateUserProfile(Long userId, String workStation, int alertThreshold) {
        if (!subwayStationRepository.existsByStationname(workStation)) {
            throw new RuntimeException("입력하신 근무역이 존재하지 않습니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setWorkStation(workStation);
        user.setAlertThreshold(alertThreshold);
        return userRepository.save(user);
    }
}
