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

    const ETIQUETAS_ESTADO = {
        EN_LINEA: 'En línea',
        FUERA_LINEA: 'Fuera de línea',
        SANO: 'Sano',
        ADVERTENCIA: 'Advertencia',
        CRITICO: 'Crítico',
        ERROR: 'Error'
    };

    function etiquetaEstado(estado) {
        return ETIQUETAS_ESTADO[estado] || estado;
    }

    function iniciarTema() {
        const boton = document.getElementById('botonTema');
        if (!boton) {
            return;
        }
        boton.addEventListener('click', () => {
            const actual = document.documentElement.dataset.theme
                || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
            const siguiente = actual === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = siguiente;
            try {
                localStorage.setItem('pulse-tema', siguiente);
            } catch (error) {
                // localStorage inaccesible: el cambio de tema sigue funcionando, solo no persiste.
            }
            document.dispatchEvent(new CustomEvent('pulse:tema-cambio'));
        });
    }

    /**
     * Controla un FAB + hoja modal generico (hoy solo "Registrar fuente" en
     * index.html, ver CHANGELOG.md). Visibilidad via el atributo `hidden`,
     * no `display` inline, para no pisar el `display:flex` que ya trae
     * `.hoja-cabecera` en panel.css.
     */
    function iniciarHoja({ boton, hoja, fondo, botonCerrar }) {
        const elBoton = document.getElementById(boton);
        const elHoja = document.getElementById(hoja);
        const elFondo = document.getElementById(fondo);
        const elCerrar = botonCerrar ? document.getElementById(botonCerrar) : null;
        if (!elBoton || !elHoja || !elFondo) {
            return;
        }

        function alEscape(evento) {
            if (evento.key === 'Escape') {
                cerrar();
            }
        }

        function abrir() {
            elHoja.hidden = false;
            elFondo.hidden = false;
            const primerCampo = elHoja.querySelector('input, select, textarea');
            if (primerCampo) {
                primerCampo.focus();
            }
            document.addEventListener('keydown', alEscape);
        }

        function cerrar() {
            elHoja.hidden = true;
            elFondo.hidden = true;
            document.removeEventListener('keydown', alEscape);
            elBoton.focus();
        }

        elBoton.addEventListener('click', abrir);
        elFondo.addEventListener('click', cerrar);
        if (elCerrar) {
            elCerrar.addEventListener('click', cerrar);
        }
    }

    function coloresGrafico() {
        const estilos = getComputedStyle(document.documentElement);
        return {
            linea: estilos.getPropertyValue('--chart-linea').trim(),
            relleno: estilos.getPropertyValue('--chart-relleno').trim(),
            texto: estilos.getPropertyValue('--chart-texto').trim(),
            grilla: estilos.getPropertyValue('--chart-grilla').trim()
        };
    }

    window.PostgresPulsePanel = { iniciarPoll, iniciarTema, iniciarHoja, coloresGrafico, etiquetaEstado };
})();
