from setuptools import find_packages, setup

setup(
    name="tia",  # Name of your package
    version="0.1.0",  # Initial version
    packages=find_packages(where="src"),  # Finds all packages in the "src" folder
    package_dir={"": "src"},  # Points to the src directory for package contents
    include_package_data=True,  # To include data files (like config files)
    data_files=[('config', ['src/config/countries.txt', 'src/config/launch_years.yml'])],
    install_requires=[],
)
