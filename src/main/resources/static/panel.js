/**
 * Auto-refresh del panel: polling simple contra endpoints propios (no
 * /api/v1/**, cuya cookie de sesion esta deliberadamente exenta de leerse
 * ahi -- ver JwtAuthenticationFilter). No es push/tiempo real: cada
 * INTERVALO_MS vuelve a pedir los datos que ya existen en la base.
 */
(function () {
    const INTERVALO_MS = 30000;

    function iniciarPoll(url, onDatos) {
        let ultimaActualizacion = null;

        async function actualizar() {
            if (document.hidden) {
                return;
            }
            try {
                const respuesta = await fetch(url, { headers: { Accept: 'application/json' } });
                if (!respuesta.ok) {
                    return;
                }
                const datos = await respuesta.json();
                ultimaActualizacion = new Date();
                onDatos(datos);
            } catch (error) {
                // Silencioso: el proximo tick reintenta solo.
            }
        }

        actualizar();
        setInterval(actualizar, INTERVALO_MS);
        setInterval(() => {
            if (!ultimaActualizacion) {
                return;
            }
            document.querySelectorAll('[data-actualizado-hace]').forEach((el) => {
                const segundos = Math.max(0, Math.round((Date.now() - ultimaActualizacion.getTime()) / 1000));
                el.textContent = 'Actualizado hace ' + segundos + 's';
            });
        }, 1000);
    }

    window.PostgresPulsePanel = { iniciarPoll };
})();
