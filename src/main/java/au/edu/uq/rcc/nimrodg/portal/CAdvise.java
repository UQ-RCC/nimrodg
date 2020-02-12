package au.edu.uq.rcc.nimrodg.portal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.sql.SQLException;

@ControllerAdvice
public class CAdvise {
	@ExceptionHandler(value = {SQLException.class, IllegalStateException.class})
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public void handleException(Exception ex) {
		ex.printStackTrace(System.err);
	}
}
