package com.postgrespulse.servicio.cifrado;

import com.postgrespulse.config.PropiedadesCifrado;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CifradoServicio {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LONGITUD_IV = 12;
    private static final int TAMANO_TAG_BITS = 128;
    private static final String PREFIJO_FORMATO = "v1";
    private static final int MINIMO_BYTES_CLAVE = 32;

    private static final SecureRandom ALEATORIO_SEGURO = new SecureRandom();

    private final byte[] claveBytes;

    public CifradoServicio(PropiedadesCifrado propiedades) {
        String clave = propiedades.getClave();
        if (clave == null || clave.getBytes(StandardCharsets.UTF_8).length < MINIMO_BYTES_CLAVE) {
            throw new IllegalStateException(
                    "PULSE_CRYPTO_KEY debe tener al menos " + MINIMO_BYTES_CLAVE + " bytes (caracteres)");
        }
        this.claveBytes = derivarClave256(clave);
    }

    public String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[LONGITUD_IV];
            ALEATORIO_SEGURO.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveBytes, "AES"), new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cifrador.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return PREFIJO_FORMATO + ":"
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(cifrado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar la credencial", ex);
        }
    }

    public String descifrar(String textoCifrado) {
        try {
            String[] partes = textoCifrado.split(":", 3);
            if (partes.length != 3 || !PREFIJO_FORMATO.equals(partes[0])) {
                throw new IllegalArgumentException("Formato de cifrado inválido");
            }
            byte[] iv = Base64.getDecoder().decode(partes[1]);
            byte[] cifrado = Base64.getDecoder().decode(partes[2]);
            Cipher descifrador = Cipher.getInstance(ALGORITMO);
            descifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(claveBytes, "AES"), new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            return new String(descifrador.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo descifrar la credencial", ex);
        }
    }

    private byte[] derivarClave256(String clave) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo derivar la clave de cifrado", ex);
        }
    }
}
