package com.postgrespulse.dominio;

/**
 * ROADMAP.md "RBAC + JWT": ADMIN puede registrar/modificar/eliminar fuentes,
 * analizar y gestionar usuarios; LECTOR solo puede consultar (paridad con
 * los endpoints GET). Sin niveles intermedios por ahora -- si aparece una
 * necesidad real de un tercer rol, se agrega entonces.
 */
public enum Rol {
    ADMIN,
    LECTOR
}
