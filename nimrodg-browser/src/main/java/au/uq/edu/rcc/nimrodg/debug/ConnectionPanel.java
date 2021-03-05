package au.uq.edu.rcc.nimrodg.debug;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.impl.postgres.NimrodAPIFactoryImpl;
import au.edu.uq.rcc.nimrodg.impl.sqlite3.SQLite3APIFactory;
import au.edu.uq.rcc.nimrodg.utils.AppDirs;
import au.edu.uq.rcc.nimrodg.utils.IniUserConfig;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.ini4j.Ini;

public class ConnectionPanel extends javax.swing.JPanel {

	private final Path localConfig;

	public ConnectionPanel() {
		initComponents();
		factoryBox.addItemListener(new _FactoryListener());
		localConfig = AppDirs.INSTANCE.configHome.resolve("nimrod.ini");
	}

	private final class _FactoryListener implements ItemListener {

		public _FactoryListener() {
			update("SQLite3");
		}

		@Override
		public void itemStateChanged(ItemEvent e) {
			if(e.getStateChange() != ItemEvent.SELECTED) {
				return;
			}

			update((String)e.getItem());
		}

		private void update(String id) {
			if("SQLite3".equals(id)) {
				driverField.setEnabled(false);
				driverField.setText("org.sqlite.JDBC");
				urlField.setEnabled(true);
				usernameField.setEnabled(false);
				passwordField.setEnabled(false);
			} else if("PostgreSQL".equals(id)) {
				driverField.setEnabled(false);
				driverField.setText("org.postgresql.Driver");
				urlField.setEnabled(true);
				usernameField.setEnabled(true);
				passwordField.setEnabled(true);
			}
		}
	
	}
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPane = new javax.swing.JTabbedPane();
        javax.swing.JPanel jPanel1 = new javax.swing.JPanel();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        factoryBox = new javax.swing.JComboBox<>();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        driverField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        urlField = new javax.swing.JTextField();
        usernameField = new javax.swing.JTextField();
        passwordField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        manualConfigArea = new javax.swing.JTextArea();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        javax.swing.JButton loadLocalBtn = new javax.swing.JButton();
        javax.swing.JButton connectBtn = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        jLabel1.setText("API Factory");

        factoryBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "SQLite3", "PostgreSQL" }));

        jLabel2.setText("Driver");

        jLabel3.setText("URL");

        jLabel4.setText("Username");

        jLabel5.setText("Password");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(driverField, javax.swing.GroupLayout.DEFAULT_SIZE, 331, Short.MAX_VALUE)
                    .addComponent(urlField)
                    .addComponent(usernameField)
                    .addComponent(passwordField)
                    .addComponent(factoryBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(factoryBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(driverField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(urlField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(usernameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabbedPane.addTab("Configuration", jPanel1);

        manualConfigArea.setColumns(20);
        manualConfigArea.setRows(5);
        jScrollPane1.setViewportView(manualConfigArea);

        jLabel6.setText("Configuration");

        loadLocalBtn.setText("Load Local");
        loadLocalBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadLocalBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(loadLocalBtn))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 422, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(loadLocalBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE)
                .addContainerGap())
        );

        tabbedPane.addTab("Manual", jPanel2);

        connectBtn.setText("Connect");
        connectBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectBtnActionPerformed(evt);
            }
        });

        jButton2.setText("Cancel");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(jButton2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(connectBtn))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(tabbedPane)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(connectBtn)
                    .addComponent(jButton2)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void loadLocalBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadLocalBtnActionPerformed
		try {
			byte[] data = Files.readAllBytes(localConfig);
			manualConfigArea.setText(new String(data, StandardCharsets.UTF_8));
			manualConfigArea.setCaretPosition(0);
		} catch(IOException e) {
			/* nop */
		}
    }//GEN-LAST:event_loadLocalBtnActionPerformed

    private void connectBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectBtnActionPerformed
        UserConfig cfg;
		switch(tabbedPane.getSelectedIndex()) {
			case 0:
				cfg = connect();
				break;
			case 1:
				cfg = connectManual();
				break;
			default:
				return;
		}

		NimrodAPIFactory factory;
		
		try {
			Class<?> clazz = Class.forName(cfg.factory());
			factory = (NimrodAPIFactory)clazz.getConstructor().newInstance();
		} catch(ReflectiveOperationException e) {
			e.printStackTrace(System.err);
			return;
		}

		try(NimrodAPI nimrod = factory.createNimrod(cfg)) {
			
		} catch(NimrodException e) {
			e.printStackTrace(System.err);
			return;
		}
    }//GEN-LAST:event_connectBtnActionPerformed

	private UserConfig connect() {
		Object id = factoryBox.getSelectedItem();
		
		UserConfig cfg;
		if("SQLite3".equals(id)) {
			return new UserConfig() {
				@Override
				public String factory() {
					return SQLite3APIFactory.class.getCanonicalName();
				}

				@Override
				public Map<String, Map<String, String>> config() {
					return Map.of("sqlite3", Map.of(
							"driver", driverField.getText(),
							"url", urlField.getText()	
					));
				}
			};
		} else if("PostgreSQL".equals(id)) {
			return new UserConfig() {
				@Override
				public String factory() {
					return NimrodAPIFactoryImpl.class.getCanonicalName();
				}

				@Override
				public Map<String, Map<String, String>> config() {
					return Map.of("postgres", Map.of(
							"driver", driverField.getText(),
							"url", urlField.getText(),
							"username", usernameField.getText(),
							"password", passwordField.getText()
					));
				}
			};
		} else {
			throw new IllegalStateException();
		}
	}

	private UserConfig connectManual() {
		Ini ini = new Ini();
		try(StringReader sr = new StringReader(manualConfigArea.getText())) {
			ini.load(sr);
		} catch(IOException e) {
			/* nop */
		}
		
		return new IniUserConfig(ini, localConfig);
	}

	private NimrodAPIFactory createFactory(UserConfig cfg) throws ReflectiveOperationException {
		Class<?> clazz = Class.forName(cfg.factory());
		return (NimrodAPIFactory)clazz.getConstructor().newInstance();
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField driverField;
    private javax.swing.JComboBox<String> factoryBox;
    private javax.swing.JButton jButton2;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea manualConfigArea;
    private javax.swing.JTextField passwordField;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JTextField urlField;
    private javax.swing.JTextField usernameField;
    // End of variables declaration//GEN-END:variables
}
