package cn.programcx.foxnaserver.core;

public interface SubTokenService {
    String generateToken(String path);
    boolean validateToken(String token, String path);
    void prolongToken(String token, String path);
}
