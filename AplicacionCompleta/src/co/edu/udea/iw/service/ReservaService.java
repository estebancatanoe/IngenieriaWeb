/**
 * 
 */
package co.edu.udea.iw.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import co.edu.udea.iw.dao.DispositivoDAO;
import co.edu.udea.iw.dao.PrestamoDAO;
import co.edu.udea.iw.dao.ReservaDAO;
import co.edu.udea.iw.dao.UsuarioDAO;
import co.edu.udea.iw.dto.Dispositivo;
import co.edu.udea.iw.dto.Prestamo;
import co.edu.udea.iw.dto.Reserva;
import co.edu.udea.iw.dto.Usuario;
import co.edu.udea.iw.exception.IWDaoException;
import co.edu.udea.iw.exception.IWServiceException;
import co.edu.udea.iw.util.dates.UtilFecha;
import co.edu.udea.iw.util.validations.Validaciones;

/**
 * Clase que implementa los m�todos de l�gica del negocio necesarios para la 
 * tabla reserva
 * @author Esteban Cata�o
 * @author Vanesa Guzman
 * @author Jeison Triana
 * @version 1
 */

@Transactional
public class ReservaService {
	private ReservaDAO reservaDao;
	private UsuarioDAO usuarioDao;
	private DispositivoDAO dispositivoDao;
	private PrestamoDAO prestamoDao;
	
	/**
	 * M�todo BL para agregar una reserva 
	 * @param codigoDispositivo Codigo del dispositivo a reservar
	 * @param usuarioInvestigador Nombre de usuario del investigador que va a prestar el dispositivo
	 * @param fechaPrestamo Fecha para la cual desea prestar el dispositivo
	 * @param cantidadHoras N�mero de horas que desea prestar el dispositivo
	 * @throws IWDaoException Manejar de Excepciones personalizado
	 * @throws IWServiceException Manejar de Excepciones personalizado
	 */
	public void agregarReserva(Long codigoDispositivo, String usuarioInvestigador,
			Date fechaPrestamo, Integer cantidadHoras) throws IWDaoException, IWServiceException{
		Reserva reserva = null;
		if(Validaciones.isTextoVacio(Long.toString(codigoDispositivo))){
			throw new IWServiceException("El c�digo del dispositivo no puede ser nulo");
		}
		if(Validaciones.isTextoVacio(usuarioInvestigador)){
			throw new IWServiceException("El nombre de usuario del investigador no puede ser nulo");
		}
		if(fechaPrestamo == null){
			throw new IWServiceException("La fecha para la que se quiere prestar no puede ser nulo");
		}
		if(fechaPrestamo.before(new Date())){
			throw new IWServiceException("La fecha no puede ser menor a la actual");
		}
		if(cantidadHoras < 1 || cantidadHoras > 8){
			throw new IWServiceException("La cantidad de horas a prestar no puede estar por fuera del"
					+ "rango espec�ficado");
		}
		Dispositivo dispositivo = dispositivoDao.obtener(codigoDispositivo);
		if(dispositivo == null){
			throw new IWServiceException("El dispositivo que desea prestar no existe");
		}
		if("SI".equals(dispositivo.getEliminado())){
			throw new IWServiceException("El dispositvo se encuentra dado de baja");
		}
		
		if(!"DISPONIBLE".equals(dispositivo.getEstado())){
			throw new IWServiceException("El dispositivo no se encuentra disponible");
		}
		
		Usuario usuario = usuarioDao.obtener(usuarioInvestigador);
		if(usuario == null){
			throw new IWServiceException("El nombre de usuario no existe");
		}
		if(!"INVESTIGADOR".equals(usuario.getRol().getNombre())){
			throw new IWServiceException("El usuario no posee el rol de investigador");
		}
		if(usuario.getFechaSancion() != null){
			throw new IWServiceException("El usuario se encuentra sancionado");
		}
		if (!this.verificarReservasUsuarios(usuario)) {
			throw new IWServiceException("El usuario tiene dispositivos sin devolver, que han"
					+ "superado la fecha l�mite");
		}
		if(!this.verificarFechaReserva(dispositivo, fechaPrestamo,cantidadHoras)){
			throw new IWServiceException("La reserva se cruza con otra reserva realizada previamente");
		}
		reserva = new Reserva();
		reserva.setDispositivo(dispositivo);
		reserva.setInvestigador(usuario);
		reserva.setFechaSolicitud(new Date());
		reserva.setFechaPrestamo(fechaPrestamo);
		reserva.setCantidadHoras(cantidadHoras);
		reserva.setAprobado("SI");
		reservaDao.insertar(reserva);
	}
	/**
	 * M�todo BL para actualizar una reserva 
	 * @param codigoReserva Codigo de la reserva que desea actualizar
	 * @param usuarioAdministracion Nombre de usuario del administrador que actualiza la reserva
	 * @param estado Indica si la aprobaci�n manual de la reserva (SI o NO) 
	 * @throws IWServiceException Manejador de excepciones personalizado
	 * @throws IWDaoException Manejador de excepcion personalizado
	 */
	public void actualizarReserva(Long codigoReserva,String usuarioAdministracion, 
			String estado) throws IWServiceException, IWDaoException{
		Reserva reserva;
		if(Validaciones.isTextoVacio(Long.toString(codigoReserva))){
			throw new IWServiceException("El c�digo de la reserva no puede ser nulo");
		}
		if(Validaciones.isTextoVacio(usuarioAdministracion)){
			throw new IWServiceException("El nombre de usuario del administrador"
					+ " no puede ser nulo");
		}
		if(Validaciones.isTextoVacio(estado)){
			throw new IWServiceException("El nuevo estado de la reserva no puede ser nulo");
		}
		Usuario usuario = usuarioDao.obtener(usuarioAdministracion);
		if(usuario == null){
			throw new IWServiceException("El usuario no existe");
		}
		if(!"ADMINISTRADOR".equals(usuario.getRol().getNombre())){
			throw new IWServiceException("El usuario no posee el rol de administrador");
		}
		reserva = reservaDao.obtener(codigoReserva);
		if(reserva == null){
			throw new IWServiceException("La reserva no existe");
		}
		reserva.setAprobado(estado);
		reserva.setAdministradorAprueba(usuario);
		reservaDao.modificar(reserva);
	}

	/**
	 * Lista todas la reservas de un usuario
	 * @param usuario Nombre de usuario del investigador
	 * @return Lista de Reservas del usuario indicado
	 * @throws IWDaoException Manejador de excepciones personalizado
	 * @throws IWServiceException 
	 */
	public List<Reserva> listarReservasUsuario(String nombreUsuario) throws IWDaoException, IWServiceException {
		/*Se crea la lista que será retornada*/
		List<Reserva> lista = null;
		if(Validaciones.isTextoVacio(nombreUsuario)){
			throw new IWServiceException("El usuario no puede ser nulo");
		}
		Usuario usuario = usuarioDao.obtener(nombreUsuario);
		if(usuario == null){
			throw new IWServiceException("El usuario no existe en la base de datos");
		}
		/*Se llena la lista con los dispositivos que no estén eliminados*/
		lista = reservaDao.obtener("investigador", usuario);
		return lista;
	}
	
	/**
	 * Verifica que la reserva no se solape con otras reserva al mismo dispositivo
	 * @param dispositivo Dispositivo a reservar
	 * @param fechaPrestamo Fecha para la que se quiere prestar el dispositivo
	 * @param cantidadHoras N�mero de horas que se desea prestar el dispositivo
	 * @return true si el dispositivo se puede prestar en lapso de tiempo indicado
	 * @throws IWDaoException Manejador de excepciones personalizado
	 */
	
	public List<Reserva> listarReservas() throws IWDaoException, IWServiceException {
		/*Se crea la lista que será retornada*/
		List<Reserva> lista = null;
		/*Se llena la lista con los dispositivos que no estén eliminados*/
		lista = reservaDao.obtener();
		return lista;
	}
	
	/**
	 * Verifica que la reserva no se solape con otras reserva al mismo dispositivo
	 * @param dispositivo Dispositivo a reservar
	 * @param fechaPrestamo Fecha para la que se quiere prestar el dispositivo
	 * @param cantidadHoras N�mero de horas que se desea prestar el dispositivo
	 * @return true si el dispositivo se puede prestar en lapso de tiempo indicado
	 * @throws IWDaoException Manejador de excepciones personalizado
	 */
	private boolean verificarFechaReserva(Dispositivo dispositivo, Date fechaPrestamo, Integer cantidadHoras) throws IWDaoException {
		// TODO Auto-generated method stub
		List<Reserva> reservas = new ArrayList<Reserva>();
		Date fechaLimite;
		Date fechaLimiteOtrasReservas;
		reservas = reservaDao.obtener("dispositivo", dispositivo);
		for(Reserva r:reservas){
			fechaLimite = UtilFecha.sumarRestarHorasFecha(fechaPrestamo, cantidadHoras);
			fechaLimiteOtrasReservas = UtilFecha.sumarRestarHorasFecha
					(r.getFechaPrestamo(), r.getCantidadHoras());
			if(fechaLimite.after(r.getFechaPrestamo()) 
					&& fechaLimite.before(fechaLimiteOtrasReservas)){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Verifica que el usuario no tenga dispositivos sin devolver que superen la fecha l�mite
	 * @param usuario Usuario que va a realizar la reserva
	 * @return true si el usuario no tiene dispositivos sin devolver fuera del plazo establecido
	 * @throws IWDaoException Manejador de excepciones personalizado
	 */
	private boolean verificarReservasUsuarios(Usuario usuario) throws IWDaoException{
		List<Reserva> reservas = new ArrayList<Reserva>();
		List<Prestamo> prestamos;
		reservas = reservaDao.obtener("investigador", usuario);
		for(Reserva r:reservas){
			prestamos = prestamoDao.obtener("codigoReserva", r);
			for(Prestamo p : prestamos){
				if(p.getFechaDevolucion() == null && 
						(p.getFechaMaximaDevolucion().before(new Date()))){
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * @return the reservaDao
	 */
	public ReservaDAO getReservaDao() {
		return reservaDao;
	}

	/**
	 * @param reservaDao the reservaDao to set
	 */
	public void setReservaDao(ReservaDAO reservaDao) {
		this.reservaDao = reservaDao;
	}

	/**
	 * @return the usuarioDao
	 */
	public UsuarioDAO getUsuarioDao() {
		return usuarioDao;
	}

	/**
	 * @param usuarioDao the usuarioDao to set
	 */
	public void setUsuarioDao(UsuarioDAO usuarioDao) {
		this.usuarioDao = usuarioDao;
	}

	/**
	 * @return the dispositivoDao
	 */
	public DispositivoDAO getDispositivoDao() {
		return dispositivoDao;
	}

	/**
	 * @param dispositivoDao the dispositivoDao to set
	 */
	public void setDispositivoDao(DispositivoDAO dispositivoDao) {
		this.dispositivoDao = dispositivoDao;
	}
	/**
	 * @return the prestamoDao
	 */
	public PrestamoDAO getPrestamoDao() {
		return prestamoDao;
	}
	/**
	 * @param prestamoDao the prestamoDao to set
	 */
	public void setPrestamoDao(PrestamoDAO prestamoDao) {
		this.prestamoDao = prestamoDao;
	}
	
}
