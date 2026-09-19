package com.ai27.app.custodio

import com.ai27.app.custodio.data.repository.CatalogoEtiquetasRepository
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

class FakeEtiquetaRepository : EtiquetaRepository by CatalogoEtiquetasRepository()