// ---------------------------------------------------------------------------
// Saneado del summary JSON de k6 antes de escribirlo.
//
// Los summaries se versionan en el repo (son la evidencia de las corridas de
// performance), pero setup() devuelve los datos del usuario de QA -incluido
// su JWT- y k6 mete ese objeto tal cual en el campo setup_data del summary.
// Un token en el repo es una credencial filtrada, aunque sea de un usuario de
// pruebas.
//
// Por eso todo handleSummary debe pasar el summary por sanitizeSummary()
// antes de serializarlo.
// ---------------------------------------------------------------------------

/**
 * Devuelve una copia del summary sin setup_data.
 *
 * @param {object} data summary que entrega k6 a handleSummary
 * @returns {object} copia sin setup_data
 */
export function sanitizeSummary(data) {
  if (!data || typeof data !== 'object') return data;
  const { setup_data: _setupData, ...rest } = data;
  return rest;
}
